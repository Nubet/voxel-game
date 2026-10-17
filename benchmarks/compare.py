import argparse
import csv
from collections import defaultdict
from pathlib import Path
from statistics import median


KEYS = ("scene", "rd", "far", "width", "height", "seed", "siteX", "siteZ",
        "clouds", "gpuTimers", "gpu", "java", "maxHeapMB", "directDraw", "measuredS")
METRICS = ("requestToUploadP95Ms", "requestToUploadP99Ms", "frameP99Ms",
           "tickP95Ms", "movementWaitingTicks", "totalAllocMBps", "gcMs")


def load(directory):
    groups = defaultdict(list)
    for path in sorted(Path(directory).glob("*.csv")):
        with path.open(newline="", encoding="utf-8") as source:
            for row in csv.DictReader(source):
                if row.get("schema") != "2":
                    raise ValueError(f"{path}: expected schema 2 and one header per file")
                if row["settleTimedOut"] != "false" or row["droppedCommands"] != "0":
                    raise ValueError(f"{path}: incomplete workload")
                # Timing jitter must not split otherwise identical scripted durations.
                key = tuple(str(round(float(row[k]))) if k == "measuredS" else row[k] for k in KEYS)
                groups[key].append(row)
    if not groups:
        raise ValueError(f"{directory}: no benchmark rows")
    return groups


def compare(baseline, candidate):
    if baseline.keys() != candidate.keys():
        raise ValueError("Runs have different scenes or environments")
    lines = []
    for key, before in baseline.items():
        after = candidate[key]
        if len({r["runId"] for r in before}) < 5 or len({r["runId"] for r in after}) < 5:
            raise ValueError("At least five independent runs per configuration are required")
        lines.append(f"{key[0]} rd={key[1]} far={key[2]}")
        for name in METRICS:
            a = [float(r[name]) for r in before]
            b = [float(r[name]) for r in after]
            left, right = median(a), median(b)
            change = f"{(right / left - 1) * 100:+.1f}%" if left else "n/a"
            lines.append(f"  {name}: {left:.3f} -> {right:.3f} ({change}); "
                         f"ranges [{min(a):.3f}, {max(a):.3f}] / [{min(b):.3f}, {max(b):.3f}]")
    return "\n".join(lines)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Compare schema-2 benchmark directories using run medians.")
    parser.add_argument("baseline")
    parser.add_argument("candidate")
    args = parser.parse_args()
    print(compare(load(args.baseline), load(args.candidate)))
