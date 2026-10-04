from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_gametest_run import verify


PASS = "3 tests are now running!\n3 GAME TESTS COMPLETE\nAll 3 required tests passed :)\nBUILD SUCCESSFUL"


class GameTestRunVerifierTest(unittest.TestCase):
    def test_completed_non_empty_run_passes(self):
        self.assertEqual([], verify(PASS, 0))

    def test_empty_run_with_green_gradle_is_rejected(self):
        self.assertTrue(verify("No test batches were given!\nBUILD SUCCESSFUL", 0))

    def test_required_failure_with_green_gradle_is_rejected(self):
        self.assertTrue(verify("3 tests are now running!\n3 GAME TESTS COMPLETE\n1 required tests failed :(\nBUILD SUCCESSFUL", 0))

    def test_incomplete_or_count_mismatch_is_rejected(self):
        self.assertTrue(verify("3 tests are now running!\nBUILD SUCCESSFUL", 0))
        self.assertTrue(verify(PASS.replace("3 GAME TESTS", "2 GAME TESTS"), 0))

    def test_process_failure_is_rejected(self):
        self.assertTrue(verify(PASS, 1))

    def test_zero_required_and_impossible_success_counts_are_rejected(self):
        self.assertTrue(verify(PASS.replace("All 3", "All 0"), 0))
        self.assertTrue(verify(PASS.replace("All 3", "All 4"), 0))

    def test_fatal_errors_after_success_summary_are_rejected(self):
        for message in ("Exception stopping the server", "Exception caught during firing event",
                        "MixinTransformerError", "InjectionError", 'Exception in thread "main"'):
            with self.subTest(message=message):
                self.assertTrue(verify(PASS + "\n" + message, 0))

    def test_build_or_datapack_failure_is_rejected(self):
        self.assertTrue(verify(PASS + "\nBUILD FAILED", 0))
        self.assertTrue(verify(PASS + "\nFailed to load datapacks", 0))

    def test_missing_loot_tables_cannot_hide_behind_passed_tests(self):
        self.assertIn("loot-table parse failure in log", verify(PASS +
                      "\nCouldn't parse element loot_tables:stardewcraft:blocks/ginger_island_sand_starfish", 0))


if __name__ == "__main__":
    unittest.main()
