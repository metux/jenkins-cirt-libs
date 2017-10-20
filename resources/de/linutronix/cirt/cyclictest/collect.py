#!/usr/bin/python

import sys
import os
from os import environ as env
from os.path import basename
import re
import numpy
from sqlalchemy import Table, MetaData
from sqlalchemy import create_engine

def create_db_connection():
    # Create an engine, connecting to the remote database
    db_type = "postgresql"
    db_host = "dbhost:port"
    db_user = "user:password"
    db_name = "RT-Test"

    db = "%s://%s@%s/%s" %(db_type, db_user, db_host, db_name)

    engine = create_engine(db)

    # get histogram and cyclictest table
    meta = MetaData()
    histogram = Table('histogram', meta, autoload=True, autoload_with=engine)

    cyclictest = Table('cyclictest', meta, autoload=True, autoload_with=engine)

    minavgmax = Table('minavgmax', meta, autoload=True, autoload_with=engine)

    # start a db connection
    try:
        con = engine.connect()
    except:
        return 1, "Problem with DB connection\n"

    return con, histogram, cyclictest, minavgmax


def submit_hist_data(con, hist_table, cyc_data, threads):
    trans = con.begin()

    try:
        for i in cyc_data:
            con.execute(hist_table.insert(),
                        thread=i[0],
                        latency=i[1],
                        count=i[2],
                        cyclictest_id=env.get("BUILD_ID"),
                        owner=env.get("ENTRYOWNER"))
        trans.commit()
    except:
        trans.rollback()
        raise


def submit_thread_data(con, mam_table, thread_data, threads):
    trans = con.begin()

    try:
        for i in range(threads):
            con.execute(mam_table.insert(),
                        **{'cyclictest_id':env.get("BUILD_ID"),
                        'thread':i,
                        'min':thread_data[0][i],
                        'avg':thread_data[1][i],
                        'max':thread_data[2][i],
                        'owner':env.get("ENTRYOWNER")})
        trans.commit()
    except:
        trans.rollback()
        raise


def get_cyclictestcmd(cmd):
    try:
        f = open(cmd, 'r')
        data = f.read()

    except IOError, e:
        print "Error %d: %s" % (e.args[0],e.args[1])
        return 1

    if f:
        f.close()
        return data


def analyze_hist_data(line, threads, cyc_data):
    # prepare data as list of type int: latency followed by
    # occurence numbers of threads
    line = line.split("\n")[0]
    values = re.split('\ |\t', line)
    values = map(int, values)

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
                values = map(int, values)
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


cyclic_env = env.get("cyclictest")

data = "result/"+cyclic_env+"/histogram.dat"
cmd = "result/"+cyclic_env+"/histogram.sh"
log = "result/"+cyclic_env+"/histogram.log"

exit(0)

con, hist_table, cyclic_table, mam_table = create_db_connection()

if con == 1:
    exit(1)

minmax, breaks, threads, cyc_data, thread_data = parse_dat_file(data)

cyclic_cmd = get_cyclictestcmd(cmd)

# submit created data into db
trans = con.begin()

try:
    # create cyclictest entry
    descr = basename(env.get("cyclictest"))
    with open(log, 'r') as fd:
      content = fd.read()
    con.execute(cyclic_table.insert(),
                **{'id':env.get("BUILD_ID"),
                   'description':descr,
                   'duration':env.get("DURATION"),
                   'interval':env.get("INTERVAL"),
                   'min':minmax[0],
                   'avg':minmax[1],
                   'max':minmax[2],
                   'boottest_id':env.get("BOOTTESTID"),
                   'pass':breaks,
                   'threshold':env.get("LIMIT"),
                   'testscript':cyclic_cmd,
                   'owner':env.get("ENTRYOWNER"),
                   'testlog':content})
    submit_hist_data(con, hist_table, cyc_data, threads)
    submit_thread_data(con, mam_table, thread_data, threads)
    trans.commit()
except:
    trans.rollback()
    raise

con.close()
