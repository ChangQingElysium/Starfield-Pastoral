from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from verify_addon_canary import (
    CALLBACK_INFO,
    CALLBACK_INFO_RETURNABLE,
    OPERATION,
    annotated_handlers,
    inject_handler_compatible,
    split_method_descriptor,
    wrap_method_handler_compatible,
)
from verify_runtime_smoke import verify_log


def line(time: str, message: str) -> str:
    return f"[267月2026 {time}] [Server thread/INFO] [{message}"


class RuntimeSmokeVerifierTest(unittest.TestCase):
    def test_accepts_complete_client_world_session(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "Reloading ResourceManager: vanilla"),
            line("10:00:01.000", "PlayerList/]: Dev logged in with entity id 1"),
            line("10:00:01.100", "Core/]: Player Dev logged in, loaded Stardew data"),
            line("10:00:21.000", "MinecraftServer/]: Stopping server"),
            line("10:00:21.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:22.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        result = verify_log(log, "client-world")

        self.assertAlmostEqual(20.0, result.duration_seconds)

    def test_rejects_abi_failure_even_when_shutdown_is_clean(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "Reloading ResourceManager: vanilla"),
            line("10:00:01.000", "PlayerList/]: Dev logged in with entity id 1"),
            "java.lang.NoSuchMethodError: broken descriptor",
            line("10:00:01.100", "Core/]: Player Dev logged in, loaded Stardew data"),
            line("10:00:21.000", "MinecraftServer/]: Stopping server"),
            line("10:00:21.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:22.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        with self.assertRaisesRegex(ValueError, "ABI or Mixin failure"):
            verify_log(log, "client-world")

    def test_rejects_title_screen_only_log(self) -> None:
        log = line("10:00:00.000", "Reloading ResourceManager: vanilla")

        with self.assertRaisesRegex(ValueError, "player login"):
            verify_log(log, "client-world")

    def test_rejects_session_that_did_not_reach_stable_ticks(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "Reloading ResourceManager: vanilla"),
            line("10:00:01.000", "PlayerList/]: Dev logged in with entity id 1"),
            line("10:00:01.100", "Core/]: Player Dev logged in, loaded Stardew data"),
            line("10:00:02.000", "MinecraftServer/]: Stopping server"),
            line("10:00:02.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:03.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        with self.assertRaisesRegex(ValueError, "minimum"):
            verify_log(log, "client-world")

    def test_accepts_fast_complete_game_test_server(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "RecipeManager]: Loaded 1498 recipes"),
            line("10:00:01.000", "GameTestServer]: Started game test server"),
            line("10:00:01.500", "GameTestServer]: All 28 required tests passed"),
            line("10:00:02.000", "MinecraftServer/]: Stopping server"),
            line("10:00:02.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:03.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        result = verify_log(log, "game-test")

        self.assertAlmostEqual(1.0, result.duration_seconds)

    def test_accepts_complete_dedicated_old_world_session(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "RecipeManager]: Loaded 1498 recipes"),
            line(
                "10:00:01.000",
                'DedicatedServer/]: Preparing level "world"',
            ),
            line(
                "10:00:02.000",
                "DedicatedServer/]: Done (0.620s)! For help, type \"help\"",
            ),
            line("10:00:12.000", "MinecraftServer/]: Stopping server"),
            line("10:00:12.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:13.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        result = verify_log(log, "dedicated-world")

        self.assertAlmostEqual(10.0, result.duration_seconds)

    def test_rejects_dedicated_world_that_never_finished_loading(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "RecipeManager]: Loaded 1498 recipes"),
            line(
                "10:00:01.000",
                'DedicatedServer/]: Preparing level "world"',
            ),
            line("10:00:12.000", "MinecraftServer/]: Stopping server"),
            line("10:00:12.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:13.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        with self.assertRaisesRegex(ValueError, "server start"):
            verify_log(log, "dedicated-world")

    def test_accepts_network_world_disconnect_and_reconnect(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "RecipeManager]: Loaded 1498 recipes"),
            line(
                "10:00:01.000",
                "DedicatedServer/]: Done (0.620s)! For help, type \"help\"",
            ),
            line(
                "10:00:02.000",
                "Core/]: Negotiated 2 Stardew network capabilities on server",
            ),
            line(
                "10:00:03.000",
                "PlayerList/]: Dev logged in with entity id 1",
            ),
            line(
                "10:00:03.100",
                "Core/]: Player Dev logged in, loaded Stardew data",
            ),
            line(
                "10:00:18.000",
                "Core/]: Player Dev logged out, saved Stardew data",
            ),
            line(
                "10:00:20.000",
                "Core/]: Negotiated 2 Stardew network capabilities on server",
            ),
            line(
                "10:00:21.000",
                "PlayerList/]: Dev logged in with entity id 2",
            ),
            line(
                "10:00:21.100",
                "Core/]: Player Dev logged in, loaded Stardew data",
            ),
            line(
                "10:00:36.000",
                "Core/]: Player Dev logged out, saved Stardew data",
            ),
            line("10:00:38.000", "MinecraftServer/]: Stopping server"),
            line("10:00:38.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:39.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        result = verify_log(log, "network-world")

        self.assertAlmostEqual(35.0, result.duration_seconds)

    def test_rejects_login_without_capability_negotiation(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "RecipeManager]: Loaded 1498 recipes"),
            line(
                "10:00:01.000",
                "DedicatedServer/]: Done (0.620s)! For help, type \"help\"",
            ),
            line(
                "10:00:03.000",
                "PlayerList/]: Dev logged in with entity id 1",
            ),
            line(
                "10:00:03.100",
                "Core/]: Player Dev logged in, loaded Stardew data",
            ),
            line("10:00:18.000", "MinecraftServer/]: Stopping server"),
            line("10:00:18.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:19.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        with self.assertRaisesRegex(
            ValueError, "initial capability negotiation"
        ):
            verify_log(log, "network-world")

    def test_rejects_network_world_without_reconnect(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "RecipeManager]: Loaded 1498 recipes"),
            line(
                "10:00:01.000",
                "DedicatedServer/]: Done (0.620s)! For help, type \"help\"",
            ),
            line(
                "10:00:02.000",
                "Core/]: Negotiated 2 Stardew network capabilities on server",
            ),
            line(
                "10:00:03.000",
                "PlayerList/]: Dev logged in with entity id 1",
            ),
            line(
                "10:00:03.100",
                "Core/]: Player Dev logged in, loaded Stardew data",
            ),
            line(
                "10:00:18.000",
                "Core/]: Player Dev logged out, saved Stardew data",
            ),
            line("10:00:20.000", "MinecraftServer/]: Stopping server"),
            line("10:00:20.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:21.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        with self.assertRaisesRegex(
            ValueError, "reconnect capability negotiation"
        ):
            verify_log(log, "network-world")

    def test_rejects_short_reconnect_session(self) -> None:
        log = "\n".join((
            line("10:00:00.000", "RecipeManager]: Loaded 1498 recipes"),
            line(
                "10:00:01.000",
                "DedicatedServer/]: Done (0.620s)! For help, type \"help\"",
            ),
            line(
                "10:00:02.000",
                "Core/]: Negotiated 2 Stardew network capabilities on server",
            ),
            line(
                "10:00:03.000",
                "PlayerList/]: Dev logged in with entity id 1",
            ),
            line(
                "10:00:03.100",
                "Core/]: Player Dev logged in, loaded Stardew data",
            ),
            line(
                "10:00:18.000",
                "Core/]: Player Dev logged out, saved Stardew data",
            ),
            line(
                "10:00:20.000",
                "Core/]: Negotiated 2 Stardew network capabilities on server",
            ),
            line(
                "10:00:21.000",
                "PlayerList/]: Dev logged in with entity id 2",
            ),
            line(
                "10:00:21.100",
                "Core/]: Player Dev logged in, loaded Stardew data",
            ),
            line(
                "10:00:22.000",
                "Core/]: Player Dev logged out, saved Stardew data",
            ),
            line("10:00:30.000", "MinecraftServer/]: Stopping server"),
            line("10:00:30.100", "MinecraftServer/]: Saving worlds"),
            line(
                "10:00:31.000",
                "MinecraftServer/]: ThreadedAnvilChunkStorage: "
                "All dimensions are saved",
            ),
        ))

        with self.assertRaisesRegex(
            ValueError, "reconnect network session lasted"
        ):
            verify_log(log, "network-world")


class AddonCanaryDescriptorTest(unittest.TestCase):
    def test_splits_arrays_and_objects(self) -> None:
        arguments, result = split_method_descriptor(
            "([ILjava/lang/String;[[Z)Ljava/util/List;"
        )

        self.assertEqual(
            ["[I", "Ljava/lang/String;", "[[Z"],
            arguments,
        )
        self.assertEqual("Ljava/util/List;", result)

    def test_inject_handler_requires_captured_argument_prefix(self) -> None:
        target = "(Ljava/lang/String;I)Z"
        good = (
            "(Ljava/lang/String;I"
            f"{CALLBACK_INFO_RETURNABLE})V"
        )
        stale = f"(Ljava/lang/String;{CALLBACK_INFO_RETURNABLE})V"

        self.assertTrue(inject_handler_compatible(target, good))
        self.assertTrue(inject_handler_compatible(target, stale))
        self.assertFalse(inject_handler_compatible(
            target,
            f"(I{CALLBACK_INFO_RETURNABLE})V",
        ))
        self.assertFalse(inject_handler_compatible(
            target,
            f"(Ljava/lang/String;I{CALLBACK_INFO})V",
        ))

    def test_wrap_method_requires_exact_target_descriptor(self) -> None:
        self.assertTrue(wrap_method_handler_compatible(
            "(Ljava/lang/String;)Z",
            f"(Ljava/lang/String;{OPERATION})Z",
        ))
        self.assertFalse(wrap_method_handler_compatible(
            "(Ljava/lang/String;I)Z",
            f"(Ljava/lang/String;{OPERATION})Z",
        ))

    def test_finds_multiline_annotated_handler(self) -> None:
        source = """
            @Inject(
                method = {"first", "second(I)V"},
                at = @At("HEAD")
            )
            private static void addon$handler(
                int value,
                CallbackInfo callback
            ) {
            }
        """

        self.assertEqual(
            [("Inject", ["first", "second(I)V"], "addon$handler")],
            annotated_handlers(source),
        )


if __name__ == "__main__":
    unittest.main()
