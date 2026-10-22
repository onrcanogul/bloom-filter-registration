#!/usr/bin/env python3
"""Runs benchmark scenarios against a running app and prints one line per scenario.

usage: python3 scripts/benchmark.py [base_url]
"""
import json
import sys
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
# (lookups, taken ratio, simulated DB latency in microseconds)
CASES = [(2000, 0.2, 1000), (2000, 0.05, 1000), (2000, 0.5, 1000), (2000, 0.2, 0), (20000, 0.2, 0)]

stats = json.load(urllib.request.urlopen(f"{BASE}/api/stats"))["bloomFilter"]
print(f"filter: expectedInsertions={stats['expectedInsertions']} size={stats['sizeInBytes'] / 1024:.0f} KB "
      f"currentFpp={stats['currentFalsePositiveRate'] * 100:.3f}%")

for lookups, ratio, latency in CASES:
    request = urllib.request.Request(f"{BASE}/api/benchmark", method="POST",
                                     data=json.dumps({"lookups": lookups, "takenRatio": ratio,
                                                      "latencyMicros": latency}).encode(),
                                     headers={"Content-Type": "application/json"})
    r = json.load(urllib.request.urlopen(request, timeout=600))
    off, on = r["withoutBloom"], r["withBloom"]
    print(f"n={lookups} taken={r['takenProbes']} latency={latency}us | "
          f"DB {off['databaseLookups']} -> {on['databaseLookups']} (-{r['databaseLookupsSavedPercent']:.1f}%) | "
          f"time {off['elapsedMillis']:.1f} -> {on['elapsedMillis']:.1f} ms (x{r['speedup']:.2f}) | "
          f"false positives {r['falsePositives']} ({r['observedFalsePositiveRate'] * 100:.2f}%) | "
          f"wrong answers {r['mismatchedAnswers']}")
