import unittest
import csv
import tempfile
from pathlib import Path
from compare import compare, load, KEYS, METRICS


class CompareTest(unittest.TestCase):
    def test_rejects_diagnostic_and_duplicate_runs(self):
        row = {key: "10" for key in KEYS}
        row.update(schema="2", runId="one", settleTimedOut="false", droppedCommands="0", jfr="true")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "run.csv"
            def write(rows):
                with path.open("w", newline="", encoding="utf-8") as target:
                    writer = csv.DictWriter(target, fieldnames=row.keys())
                    writer.writeheader()
                    writer.writerows(rows)
            write([row])
            with self.assertRaises(ValueError):
                load(directory)
            row["jfr"] = "false"
            write([row, row])
            with self.assertRaises(ValueError):
                load(directory)

    def test_requires_independent_runs_and_matching_workloads(self):
        rows = [dict(runId=str(i), **{k: str(i + 1) for k in METRICS}) for i in range(5)]
        key = ("FLY_NEW", "8", "64")
        self.assertIn("3.000 -> 3.000", compare({key: rows}, {key: rows}))
        with self.assertRaises(ValueError):
            compare({key: rows}, {})
        with self.assertRaises(ValueError):
            compare({key: rows[:1]}, {key: rows})


if __name__ == "__main__":
    unittest.main()
