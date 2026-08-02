package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatHitCooldownPolicyContractTest {
    private static final Pattern DIRECT_COOLDOWN_RESET = Pattern.compile(
            "\\.(?:invulnerableTime|hurtTime)\\s*=\\s*0\\s*;"
    );
    private static final Pattern INVULNERABLE_TIME_RESET = Pattern.compile(
            "\\.invulnerableTime\\s*=\\s*0\\s*;"
    );
    private static final Pattern HURT_TIME_RESET = Pattern.compile(
            "\\.hurtTime\\s*=\\s*0\\s*;"
    );
    private static final Pattern WEAPON_SKILL_DAMAGE_CALL = Pattern.compile(
            "\\bWeaponSkillDamage\\.(?:applyWithResult|apply)\\s*\\("
    );

    @Test
    void combatDamagePathsNeverResetEntityCooldownFieldsDirectly()
            throws IOException {
        Path mainJava = projectRoot().resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft"
        ));
        Path mummyReviveSource = mainJava.resolve(Path.of(
                "event", "MineMonsterSpawnHandler.java"
        ));
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(mainJava)) {
            for (Path source : paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.equals(mummyReviveSource))
                    .toList()) {
                if (DIRECT_COOLDOWN_RESET.matcher(
                        Files.readString(source)
                ).find()) {
                    violations.add(mainJava.relativize(source).toString());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "Use HitCooldownPolicy/HitCooldownDamageSource instead: "
                        + violations
        );
    }

    @Test
    void mummyReviveFlowRetainsItsFourIntentionalStateResets()
            throws IOException {
        String source = Files.readString(projectRoot().resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "event", "MineMonsterSpawnHandler.java"
        )));

        assertEquals(4, occurrences(source, INVULNERABLE_TIME_RESET));
        assertEquals(4, occurrences(source, HURT_TIME_RESET));
    }

    @Test
    void everyProductionDamageEmissionDeclaresBothPolicies()
            throws IOException {
        Path mainJava = projectRoot().resolve(Path.of(
                "src", "main", "java", "com", "stardew", "craft"
        ));
        List<String> violations = new ArrayList<>();
        int callCount = 0;

        try (Stream<Path> paths = Files.walk(mainJava)) {
            for (Path sourcePath : paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals(
                            "WeaponSkillDamage.java"
                    ))
                    .toList()) {
                String source = maskCommentsAndLiterals(
                        Files.readString(sourcePath)
                );
                Matcher matcher = WEAPON_SKILL_DAMAGE_CALL.matcher(source);
                while (matcher.find()) {
                    callCount++;
                    List<String> arguments = parseArguments(
                            source,
                            matcher.end() - 1
                    );
                    boolean explicitAttackGate = arguments.size() >= 2
                            && isExplicitPolicy(
                                    arguments.get(arguments.size() - 2),
                                    "AttackGatePolicy",
                                    "attackGatePolicy"
                            );
                    boolean explicitHitCooldown = !arguments.isEmpty()
                            && isExplicitPolicy(
                                    arguments.get(arguments.size() - 1),
                                    "HitCooldownPolicy",
                                    "hitCooldownPolicy"
                            );
                    if (!explicitAttackGate || !explicitHitCooldown) {
                        violations.add(
                                mainJava.relativize(sourcePath)
                                        + ":"
                                        + lineNumber(source, matcher.start())
                        );
                    }
                }
            }
        }

        assertTrue(callCount > 0, "No WeaponSkillDamage calls were scanned");
        assertTrue(
                violations.isEmpty(),
                "Every production damage emission must explicitly pass "
                        + "AttackGatePolicy and HitCooldownPolicy: "
                        + violations
        );
    }

    private static long occurrences(String source, Pattern pattern) {
        return pattern.matcher(source).results().count();
    }

    private static boolean isExplicitPolicy(
            String argument,
            String policyType,
            String parameterName
    ) {
        String compact = argument.replaceAll("\\s+", "");
        return compact.contains(policyType + ".")
                || compact.equals(parameterName);
    }

    private static List<String> parseArguments(
            String source,
            int openingParenthesis
    ) {
        List<String> arguments = new ArrayList<>();
        int parentheses = 1;
        int brackets = 0;
        int braces = 0;
        int argumentStart = openingParenthesis + 1;

        for (int index = argumentStart; index < source.length(); index++) {
            char current = source.charAt(index);
            switch (current) {
                case '(' -> parentheses++;
                case ')' -> {
                    parentheses--;
                    if (parentheses == 0) {
                        addArgument(
                                arguments,
                                source.substring(argumentStart, index)
                        );
                        return arguments;
                    }
                }
                case '[' -> brackets++;
                case ']' -> brackets--;
                case '{' -> braces++;
                case '}' -> braces--;
                case ',' -> {
                    if (parentheses == 1 && brackets == 0 && braces == 0) {
                        addArgument(
                                arguments,
                                source.substring(argumentStart, index)
                        );
                        argumentStart = index + 1;
                    }
                }
                default -> {
                }
            }
        }
        throw new IllegalArgumentException(
                "Unbalanced WeaponSkillDamage call at index "
                        + openingParenthesis
        );
    }

    private static void addArgument(
            List<String> arguments,
            String argument
    ) {
        if (!argument.isBlank()) {
            arguments.add(argument.trim());
        }
    }

    private static String maskCommentsAndLiterals(String source) {
        StringBuilder masked = new StringBuilder(source.length());
        int state = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length()
                    ? source.charAt(index + 1)
                    : '\0';
            if (state == 0) {
                if (current == '/' && next == '/') {
                    masked.append("  ");
                    index++;
                    state = 1;
                } else if (current == '/' && next == '*') {
                    masked.append("  ");
                    index++;
                    state = 2;
                } else if (current == '"') {
                    masked.append(' ');
                    state = 3;
                } else if (current == '\'') {
                    masked.append(' ');
                    state = 4;
                } else {
                    masked.append(current);
                }
            } else if (state == 1) {
                masked.append(current == '\n' ? '\n' : ' ');
                if (current == '\n') {
                    state = 0;
                }
            } else if (state == 2) {
                if (current == '*' && next == '/') {
                    masked.append("  ");
                    index++;
                    state = 0;
                } else {
                    masked.append(current == '\n' ? '\n' : ' ');
                }
            } else {
                masked.append(current == '\n' ? '\n' : ' ');
                if (current == '\\' && next != '\0') {
                    masked.append(next == '\n' ? '\n' : ' ');
                    index++;
                } else if ((state == 3 && current == '"')
                        || (state == 4 && current == '\'')) {
                    state = 0;
                }
            }
        }
        return masked.toString();
    }

    private static int lineNumber(String source, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("stardewcraft.projectDir", "."))
                .toAbsolutePath()
                .normalize();
    }
}
