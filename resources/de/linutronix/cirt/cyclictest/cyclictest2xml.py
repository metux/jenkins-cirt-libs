#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2018 Linutronix GmbH

from junit_xml import TestSuite, TestCase
import sys
import os
from os import environ as env
from os.path import basename, join
import re
import numpy
import argparse


def submit_hist_data(cyc_data, threads, suite, entryowner):
    for idx, val in enumerate(cyc_data):
        suite.properties['hist_data{}'.format(idx)] = "{},{},{},{}".format(
            val[0],                 # thread
            val[1],                 # latency
            val[2],                 # count
            entryowner              # owner
            )
    return suite


def submit_thread_data(thread_data, threads, suite, entryowner):
    for i in range(threads):
        suite.properties['thread_data{}'.format(i)] = "{},{},{},{},{}".format(
            i,                      # thread
            thread_data[0][i],      # min
            thread_data[1][i],      # avg
            thread_data[2][i],      # max
            entryowner              # owner
            )
    return suite


def get_cyclictestcmd(cmd):
    try:
        f = open(cmd, 'r')
        data = f.read()

    except IOError as e:
        print("Error %d: %s" % (e.args[0], e.args[1]))
        return 1

    if f:
        f.close()
        return data


def analyze_hist_data(line, threads, cyc_data):
    # prepare data as list of type int: latency followed by
    # occurence numbers of threads
    line = line.split("\n")[0]
    values = re.split('\ |\t', line)
    values = list(map(int, values))

    latency = values[0]

    # if occurence on all threads is zero, nothing has to be done
    if all(v == 0 for v in values[1:]):
        return cyc_data

    # remove latency value from array, that there are only the
    # occurence numbers of the threads left
    del values[0]

    # create array of lists with thread, latency, occurence number
    for i in range(threads):
        if values[i] != 0:
            cyc_data.append([i, latency, values[i]])

    return cyc_data


def parse_dat_file(data):
    threads = 0
    breaks = True
    minmax = [0] * 3
    cyc_data = []
    thread_data = []

    # lines with Max, Avg, Min and Break after # should not be ignored
    # all other lines starting with # should be ignored!
    # Break thread also can be ignored
    re_ignore = re.compile("^# [^MAB]")
    re_empty = re.compile("^\n")
    re_breakthread = re.compile("^# Break thread")

    re_minavgmax = re.compile("^# [MA]")
    re_max = re.compile("^# Max")
    re_min = re.compile("^# Min")
    re_avg = re.compile("^# Avg")

    re_break = re.compile("^# Break value")

    with open(data, 'r') as d:
        for line in d:
            # all ignore cases
            if re_ignore.search(line) or re_empty.search(line)\
               or re_breakthread.search(line):
                continue

            # if min, max or avg, take only needed values and type
            # cast to int
            elif re_minavgmax.search(line):
                buf = line.split(":")[1].split("\n")[0]
                values = buf.split(" ")[1:]
                values = list(map(int, values))
                thread_data.append(values[:])

                # write min value to array
                if re_min.search(line):
                    minmax[0] = min(values)

                elif re_avg.search(line):
                    # remove zeros of values, then calculate avg
                    if 0 in values:
                        values.remove(0)
                    avg = numpy.mean(values)
                    minmax[1] = int(round(avg))

                # write max value to array
                elif re_max.search(line):
                    minmax[2] = int(max(values))

            # if there is a break line, set break (pass = false)
            elif re_break.search(line):
                breaks = False

            # calculate cyclictest thread number (will be done in the
            # first data line, latency of zero cannot exist)
            elif threads == 0:
                threads = len(line.split()) - 1

            else:
                cyc_data = analyze_hist_data(line, threads, cyc_data)

    return minmax, breaks, threads, cyc_data, thread_data

parser = argparse.ArgumentParser()
parser.add_argument('cyclic_env')
parser.add_argument('cyclic_dir')
parser.add_argument('--entryowner', required=True)
parser.add_argument('--duration', required=True)
parser.add_argument('--interval', required=True)
parser.add_argument('--limit', required=True)
args = parser.parse_args()

cyclic_env = args.cyclic_env
cyclic_dir = args.cyclic_dir
descr = basename(cyclic_env)

data = join(cyclic_dir, "histogram.dat")
cmd = join(cyclic_dir, "histogram.sh")
log = join(cyclic_dir, "histogram.log")

# use data as system-out
# and log as system-err

minmax, breaks, threads, cyc_data, thread_data = parse_dat_file(data)

cyclic_cmd = get_cyclictestcmd(cmd)

case = TestCase(cyclic_env)
case.classname = "cyclictest"
ts = TestSuite("suite")

if not breaks:
    case.add_failure_info("failure", None)

with open(log, 'r') as fd:
    system_err = fd.read()
with open(data, 'r') as fd:
    system_out = fd.read()
ts.properties = {}
ts.properties['description'] = descr
ts.properties['duration'] = args.duration
ts.properties['interval'] = args.interval
ts.properties['min'] = minmax[0]
ts.properties['avg'] = minmax[1]
ts.properties['max'] = minmax[2]
ts.properties['pass'] = breaks
ts.properties['threshold'] = args.limit
ts.properties['testscript'] = cyclic_cmd
ts.properties['owner'] = args.entryowner

ts = submit_hist_data(cyc_data, threads, ts, args.entryowner)
ts = submit_thread_data(thread_data, threads, ts, args.entryowner)
case.stdout = system_out
case.stderr = system_err

ts.test_cases.append(case)

with open(join(cyclic_dir, 'pyjutest.xml'), 'w') as f:
    TestSuite.to_file(f, [ts], prettyprint=True)
