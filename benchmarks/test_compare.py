import unittest
from compare import compare, METRICS


class CompareTest(unittest.TestCase):
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
