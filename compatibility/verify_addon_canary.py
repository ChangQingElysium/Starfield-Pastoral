#!/usr/bin/env python3
"""Verify one pinned addon's declared compatibility surface."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path


METHOD_ANNOTATIONS = (
    "Inject",
    "Redirect",
    "ModifyArg",
    "ModifyArgs",
    "ModifyConstant",
    "ModifyExpressionValue",
    "ModifyReceiver",
    "ModifyReturnValue",
    "ModifyVariable",
    "WrapMethod",
    "WrapOperation",
)

CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;"
CALLBACK_INFO_RETURNABLE = (
    "Lorg/spongepowered/asm/mixin/injection/callback/"
    "CallbackInfoReturnable;"
)
OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;"


def fail(message: str) -> None:
    raise SystemExit(f"Addon canary verification failed: {message}")


def annotation_body(source: str, opening_paren: int) -> str:
    depth = 0
    in_string = False
    escaped = False
    for index in range(opening_paren, len(source)):
        char = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return source[opening_paren + 1 : index]
    fail("unterminated annotation")
    return ""


def closing_paren(source: str, opening_paren: int) -> int:
    depth = 0
    in_string = False
    escaped = False
    for index in range(opening_paren, len(source)):
        char = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return index
    fail("unterminated annotation")
    return -1


def named_argument(body: str, name: str) -> str | None:
    match = re.search(rf"\b{re.escape(name)}\s*=", body)
    if match is None:
        return None
    start = match.end()
    depths = {"(": 0, "{": 0, "[": 0}
    pairs = {")": "(", "}": "{", "]": "["}
    in_string = False
    escaped = False
    for index in range(start, len(body)):
        char = body[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char in depths:
            depths[char] += 1
        elif char in pairs:
            depths[pairs[char]] -= 1
        elif char == "," and all(depth == 0 for depth in depths.values()):
            return body[start:index].strip()
    return body[start:].strip()


def string_literals(expression: str) -> list[str]:
    values = re.findall(r'"((?:\\.|[^"\\])*)"', expression)
    return [bytes(value, "utf-8").decode("unicode_escape") for value in values]


def method_selectors(source: str) -> list[str]:
    selectors: list[str] = []
    annotation_pattern = "|".join(METHOD_ANNOTATIONS)
    for match in re.finditer(rf"@(?:{annotation_pattern})\s*\(", source):
        body = annotation_body(source, source.find("(", match.start()))
        expression = named_argument(body, "method")
        if expression is None:
            continue
        values = string_literals(expression)
        if expression.lstrip().startswith("{"):
            selectors.extend(values)
        elif values:
            selectors.append("".join(values))
    for match in re.finditer(r"@Invoker\s*\(", source):
        body = annotation_body(source, source.find("(", match.start()))
        values = string_literals(body)
        if values:
            selectors.append(values[0])
    return selectors


def annotated_handlers(
    source: str,
) -> list[tuple[str, list[str], str]]:
    handlers: list[tuple[str, list[str], str]] = []
    annotation_pattern = "|".join(METHOD_ANNOTATIONS)
    for match in re.finditer(
        rf"@(?P<kind>{annotation_pattern})\s*\(", source
    ):
        opening = source.find("(", match.start())
        body = annotation_body(source, opening)
        method_expression = named_argument(body, "method")
        if method_expression is None:
            continue
        values = string_literals(method_expression)
        selectors = (
            values
            if method_expression.lstrip().startswith("{")
            else ["".join(values)]
        )
        end = closing_paren(source, opening)
        declaration = re.search(
            r"\b([A-Za-z_$][\w$]*)\s*"
            r"\([^;{}]*\)\s*(?:throws\s+[^{]+)?\{",
            source[end + 1 :],
            flags=re.DOTALL,
        )
        if declaration is None:
            fail(
                f"cannot resolve handler declaration after "
                f"@{match.group('kind')}"
            )
        handlers.append((
            match.group("kind"),
            selectors,
            declaration.group(1),
        ))
    return handlers


def split_method_descriptor(descriptor: str) -> tuple[list[str], str]:
    if not descriptor.startswith("("):
        fail(f"invalid method descriptor: {descriptor}")
    arguments: list[str] = []
    index = 1
    while index < len(descriptor) and descriptor[index] != ")":
        start = index
        while descriptor[index] == "[":
            index += 1
        if descriptor[index] == "L":
            end = descriptor.find(";", index)
            if end < 0:
                fail(f"invalid object descriptor: {descriptor}")
            index = end + 1
        else:
            index += 1
        arguments.append(descriptor[start:index])
    if index >= len(descriptor) or descriptor[index] != ")":
        fail(f"invalid method descriptor: {descriptor}")
    return arguments, descriptor[index + 1 :]


def descriptors_for(methods: set[str], name: str) -> list[str]:
    prefix = name + "("
    return sorted(
        entry[len(name) :]
        for entry in methods
        if entry.startswith(prefix)
    )


def selector_descriptors(
    methods: set[str],
    selector: str,
) -> list[str]:
    if "(" in selector:
        name = selector.split("(", 1)[0]
        descriptor = selector[len(name) :]
        return [descriptor] if selector in methods else []
    return descriptors_for(methods, selector)


def inject_handler_compatible(
    target_descriptor: str,
    handler_descriptor: str,
) -> bool:
    target_arguments, target_return = split_method_descriptor(
        target_descriptor
    )
    handler_arguments, handler_return = split_method_descriptor(
        handler_descriptor
    )
    if handler_return != "V":
        return False
    callback_indexes = [
        index
        for index, argument in enumerate(handler_arguments)
        if argument in (CALLBACK_INFO, CALLBACK_INFO_RETURNABLE)
    ]
    if len(callback_indexes) != 1:
        return False
    callback_index = callback_indexes[0]
    captured_arguments = handler_arguments[:callback_index]
    if captured_arguments != target_arguments[:len(captured_arguments)]:
        return False
    callback = handler_arguments[callback_index]
    if target_return == "V":
        return callback == CALLBACK_INFO
    return callback == CALLBACK_INFO_RETURNABLE


def wrap_method_handler_compatible(
    target_descriptor: str,
    handler_descriptor: str,
) -> bool:
    target_arguments, target_return = split_method_descriptor(
        target_descriptor
    )
    handler_arguments, handler_return = split_method_descriptor(
        handler_descriptor
    )
    return (
        handler_return == target_return
        and handler_arguments == target_arguments + [OPERATION]
    )


def verify_handler_descriptors(
    mixin_name: str,
    source: str,
    target_methods: set[str],
    handler_methods: set[str],
) -> int:
    checked = 0
    for kind, selectors, handler_name in annotated_handlers(source):
        if kind not in ("Inject", "WrapMethod"):
            continue
        handler_descriptors = descriptors_for(
            handler_methods, handler_name
        )
        if not handler_descriptors:
            fail(
                f"{mixin_name} compiled handler is absent: "
                f"{handler_name}"
            )
        for selector in selectors:
            target_descriptors = selector_descriptors(
                target_methods, selector
            )
            if not target_descriptors:
                fail(
                    f"{mixin_name} handler {handler_name} has no "
                    f"target descriptor for {selector}"
                )
            compatibility = (
                inject_handler_compatible
                if kind == "Inject"
                else wrap_method_handler_compatible
            )
            if not any(
                compatibility(target, handler)
                for target in target_descriptors
                for handler in handler_descriptors
            ):
                fail(
                    f"{mixin_name} @{kind} handler {handler_name} "
                    f"{handler_descriptors} is incompatible with "
                    f"{selector} {target_descriptors}"
                )
            checked += 1
    return checked


def accessor_fields(source: str) -> list[str]:
    fields: list[str] = []
    for match in re.finditer(r"@Accessor\s*\(", source):
        body = annotation_body(source, source.find("(", match.start()))
        values = string_literals(body)
        if values:
            fields.append(values[0])
    return fields


def shadow_fields(source: str) -> list[str]:
    fields: list[str] = []
    pattern = re.compile(
        r"@Shadow(?:\s*\([^)]*\))?"
        r"(?:\s+@\w+(?:\s*\([^)]*\))?)*"
        r"\s+(?P<declaration>[^;]+;)",
        flags=re.MULTILINE,
    )
    for match in pattern.finditer(source):
        declaration = match.group("declaration")
        field_match = re.search(r"([A-Za-z_$][\w$]*)\s*;$", declaration.strip())
        if field_match is not None:
            fields.append(field_match.group(1))
    return fields


def injection_invocation_requirements(
    source: str,
) -> list[tuple[str, list[str], str, int]]:
    requirements: list[tuple[str, list[str], str, int]] = []
    annotation_pattern = "|".join(METHOD_ANNOTATIONS)
    for match in re.finditer(rf"@(?P<kind>{annotation_pattern})\s*\(", source):
        body = annotation_body(source, source.find("(", match.start()))
        method_expression = named_argument(body, "method")
        at_expression = named_argument(body, "at")
        if method_expression is None or at_expression is None:
            continue
        selectors = string_literals(method_expression)
        invocation_targets = [
            value for value in string_literals(at_expression)
            if value.startswith("L") and "(" in value
        ]
        require_expression = named_argument(body, "require")
        required = 1
        if require_expression is not None:
            require_match = re.match(r"\s*(\d+)", require_expression)
            if require_match is not None:
                required = int(require_match.group(1))
        for invocation_target in invocation_targets:
            requirements.append((
                match.group("kind"),
                selectors,
                invocation_target,
                required,
            ))
    return requirements


def mixin_targets(source: str) -> list[str]:
    match = re.search(r"@Mixin\s*\(", source)
    if match is None:
        fail("mixin source has no @Mixin annotation")
    body = annotation_body(source, source.find("(", match.start()))
    imports = {
        imported.rsplit(".", 1)[-1]: imported
        for imported in re.findall(r"^import\s+([\w.$]+);", source, flags=re.MULTILINE)
    }

    targets: list[str] = []
    for token in re.findall(r"\b([A-Za-z_][\w.]*)\.class\b", body):
        first, *nested = token.split(".")
        resolved = imports.get(first)
        if resolved is None:
            fail(f"cannot resolve @Mixin target {token}")
        if nested:
            resolved += "$" + "$".join(nested)
        targets.append(resolved)

    targets_expression = named_argument(body, "targets")
    if targets_expression is not None:
        targets.extend(string_literals(targets_expression))
    return sorted(set(targets))


def javap_members(javap: str, class_file: Path) -> tuple[set[str], set[str]]:
    result = subprocess.run(
        [javap, "-private", "-s", str(class_file)],
        check=True,
        capture_output=True,
        text=True,
    )
    methods: set[str] = set()
    fields: set[str] = set()
    pending: str | None = None
    for raw_line in result.stdout.splitlines():
        line = raw_line.strip()
        if line.startswith("descriptor:") and pending is not None:
            descriptor = line.removeprefix("descriptor:").strip()
            if pending == "static {};":
                methods.add("<clinit>")
                methods.add("<clinit>" + descriptor)
            elif "(" in pending:
                name = pending.split("(", 1)[0].split()[-1]
                if "." in name:
                    name = "<init>"
                methods.add(name)
                methods.add(name + descriptor)
            else:
                field_name = pending.removesuffix(";").split()[-1]
                if "=" in field_name:
                    field_name = field_name.split("=", 1)[0]
                fields.add(field_name)
            pending = None
        elif line.endswith(";"):
            pending = line
    return methods, fields


def javap_invocation_counts(
    javap: str,
    class_file: Path,
) -> dict[str, dict[str, int]]:
    result = subprocess.run(
        [javap, "-private", "-c", str(class_file)],
        check=True,
        capture_output=True,
        text=True,
    )
    counts: dict[str, dict[str, int]] = {}
    current_method: str | None = None
    for raw_line in result.stdout.splitlines():
        if (
            raw_line.startswith("  ")
            and not raw_line.startswith("    ")
            and raw_line.strip().endswith(";")
            and "(" in raw_line
        ):
            declaration = raw_line.strip()
            name = declaration.split("(", 1)[0].split()[-1]
            if "." in name:
                name = "<init>"
            current_method = name
            counts.setdefault(current_method, {})
            continue
        if current_method is None or "//" not in raw_line:
            continue
        comment = raw_line.split("//", 1)[1].strip()
        if comment.startswith("Method "):
            invocation = comment.removeprefix("Method ").strip()
        elif comment.startswith("InterfaceMethod "):
            invocation = comment.removeprefix("InterfaceMethod ").strip()
        else:
            continue
        method_counts = counts.setdefault(current_method, {})
        method_counts[invocation] = method_counts.get(invocation, 0) + 1
    return counts


def normalize_invocation_target(target: str) -> str:
    match = re.fullmatch(r"L([^;]+);([^(]+)(\(.*)", target)
    if match is None:
        fail(f"unsupported redirect invocation target: {target}")
    return f"{match.group(1)}.{match.group(2)}:{match.group(3)}"


def class_file_for(core_classes: Path, target: str) -> Path:
    direct = core_classes / (target.replace(".", "/") + ".class")
    if direct.is_file():
        return direct
    fail(f"Mixin target class is absent from current main build: {target}")
    return direct


def main() -> None:
    if len(sys.argv) not in (3, 5):
        fail(
            "usage: verify_addon_canary.py <addon-id> "
            "<checked-out-addon-directory> "
            "[--addon-classes <compiled-class-directory>]"
        )

    project_root = Path(__file__).resolve().parents[1]
    addon_id = sys.argv[1]
    addon_root = Path(sys.argv[2]).resolve()
    addon_classes: Path | None = None
    if len(sys.argv) == 5:
        if sys.argv[3] != "--addon-classes":
            fail(f"unknown option: {sys.argv[3]}")
        addon_classes = Path(sys.argv[4]).resolve()
        if not addon_classes.is_dir():
            fail(
                f"compiled addon class directory is absent: "
                f"{addon_classes}"
            )
    core_classes = project_root / "build/classes/java/main"
    manifest = json.loads(
        (project_root / "compatibility/addon-canaries.json").read_text(encoding="utf-8")
    )
    addon = next(
        (entry for entry in manifest["addons"] if entry["id"] == addon_id),
        None,
    )
    if addon is None:
        fail(f"{addon_id} is missing from addon-canaries.json")

    actual_commit = subprocess.run(
        ["git", "-C", str(addon_root), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if actual_commit != addon["commit"]:
        fail(f"expected commit {addon['commit']}, found {actual_commit}")

    mixin_config = json.loads(
        (addon_root / addon["mixin_config"]).read_text(encoding="utf-8")
    )
    configured_mixins = sorted(mixin_config.get("mixins", []) + mixin_config.get("client", []))
    expected_mixins = sorted(addon["expected_mixins"])
    if configured_mixins != expected_mixins:
        missing = sorted(set(expected_mixins) - set(configured_mixins))
        unexpected = sorted(set(configured_mixins) - set(expected_mixins))
        fail(f"Mixin set changed; missing={missing}, unexpected={unexpected}")

    javap = "javap"
    checked_targets: set[str] = set()
    checked_selectors = 0
    checked_accessors = 0
    checked_shadows = 0
    checked_injection_invocations = 0
    checked_handler_descriptors = 0
    for mixin_name in expected_mixins:
        source_path = addon_root / addon["mixin_source_root"] / f"{mixin_name}.java"
        if not source_path.is_file():
            fail(f"missing Mixin source: {source_path}")
        source = source_path.read_text(encoding="utf-8")
        handler_methods: set[str] | None = None
        if addon_classes is not None:
            handler_class = addon_classes / (
                addon["mixin_source_root"]
                .removeprefix("src/main/java/")
                + f"/{mixin_name}.class"
            )
            if not handler_class.is_file():
                fail(f"compiled Mixin class is absent: {handler_class}")
            handler_methods, _ = javap_members(javap, handler_class)
        targets = mixin_targets(source)
        if not targets:
            fail(f"{mixin_name} has no resolved target")
        selectors = method_selectors(source)
        accessors = accessor_fields(source)
        shadows = shadow_fields(source)
        invocation_requirements = injection_invocation_requirements(
            source
        )
        for target in targets:
            class_file = class_file_for(core_classes, target)
            methods, fields = javap_members(javap, class_file)
            if handler_methods is not None:
                checked_handler_descriptors += (
                    verify_handler_descriptors(
                        mixin_name,
                        source,
                        methods,
                        handler_methods,
                    )
                )
            invocation_counts = javap_invocation_counts(javap, class_file)
            checked_targets.add(target)
            for selector in selectors:
                if selector not in methods:
                    fail(f"{mixin_name} expects missing method {target}::{selector}")
                checked_selectors += 1
            for field in accessors:
                if field not in fields:
                    fail(f"{mixin_name} expects missing field {target}::{field}")
                checked_accessors += 1
            for field in shadows:
                if field not in fields:
                    fail(f"{mixin_name} shadows missing field {target}::{field}")
                checked_shadows += 1
            for (
                injection_kind,
                injection_selectors,
                invocation_target,
                required,
            ) in invocation_requirements:
                normalized_target = normalize_invocation_target(invocation_target)
                matches = sum(
                    invocation_counts.get(selector, {}).get(normalized_target, 0)
                    for selector in injection_selectors
                )
                if matches < required:
                    fail(
                        f"{mixin_name} @{injection_kind} expects at least {required} "
                        f"invocation(s) of {invocation_target} in "
                        f"{target}::{injection_selectors}, found {matches}"
                    )
                checked_injection_invocations += matches

    print(
        f"verified addon canary {addon_id} "
        f"{actual_commit[:8]}: {len(expected_mixins)} mixins, "
        f"{len(checked_targets)} target classes, "
        f"{checked_selectors} method selectors, "
        f"{checked_accessors} accessor fields, "
        f"{checked_shadows} shadow fields, "
        f"{checked_handler_descriptors} handler descriptors, "
        f"{checked_injection_invocations} injection invocation points"
    )


if __name__ == "__main__":
    main()
