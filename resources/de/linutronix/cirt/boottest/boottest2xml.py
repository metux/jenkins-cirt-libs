#!/usr/bin/env python3

from junit_xml import TestSuite, TestCase
import sys
from os.path import join

boottest = sys.argv[1]
boottest_dir = sys.argv[2]
try:
    failure = sys.argv[3]
except:
    failure = False
bootlog = join(boottest_dir, "boottest", "boot.log")
cmdline_file = join(boottest_dir, "cmdline")

case = TestCase(boottest)
case.classname = "boottest"
ts = TestSuite("suite")

if failure == "failure":
    case.add_failure_info("failure", None)

with open(bootlog, 'r') as fd:
    system_out = fd.read()
with open(cmdline_file, 'r') as fd:
    cmdline = fd.read()
ts.properties = {}
ts.properties['cmdline'] = cmdline

case.stdout = system_out

ts.test_cases.append(case)

with open(join(boottest_dir, 'boottest', 'pyjutest.xml'), 'w') as f:
    TestSuite.to_file(f, [ts], prettyprint=True)
