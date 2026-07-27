import tempfile
import unittest
from pathlib import Path

from verify_api_maturity_review import (
    Evidence,
    rationale_for,
    render_review,
    verify_review,
)


class VerifyApiMaturityReviewTest(unittest.TestCase):
    def test_rationale_reports_first_missing_evidence(self):
        self.assertEqual(
            "core_runtime_reference_missing",
            rationale_for(Evidence(False, False, False, False)),
        )
        self.assertEqual(
            "independent_sample_reference_missing",
            rationale_for(Evidence(True, False, True, True)),
        )
        self.assertEqual(
            "direct_test_reference_missing",
            rationale_for(Evidence(True, True, False, True)),
        )
        self.assertEqual(
            "direct_documentation_reference_missing",
            rationale_for(Evidence(True, True, True, False)),
        )
        self.assertEqual(
            "real_network_and_second_consumer_evidence_pending",
            rationale_for(Evidence(True, True, True, True)),
        )

    def test_review_rejects_stale_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            experimental = root / "experimental.txt"
            review = root / "review.tsv"
            source = root / "src/main/java/com/stardew/craft/api/v1/example"
            source.mkdir(parents=True)
            (source / "ExampleType.java").write_text(
                "package com.stardew.craft.api.v1.example;\n"
                "public final class ExampleType {}\n",
                encoding="utf-8",
            )
            experimental.write_text(
                "com.stardew.craft.api.v1.example.ExampleType\n",
                encoding="utf-8",
            )
            review.write_text("stale\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "review is stale"):
                verify_review(experimental, review, root)

    def test_render_has_one_decision_per_type_in_sorted_order(self):
        evidence = {
            "example.B": Evidence(False, False, False, False),
            "example.A": Evidence(True, True, True, True),
        }
        rendered = render_review(sorted(evidence), evidence)
        rows = [
            line for line in rendered.splitlines() if not line.startswith("#")
        ]
        self.assertEqual(3, len(rows))
        self.assertTrue(rows[1].startswith("example.A\tkeep_experimental\t"))
        self.assertTrue(rows[2].startswith("example.B\tkeep_experimental\t"))


if __name__ == "__main__":
    unittest.main()
