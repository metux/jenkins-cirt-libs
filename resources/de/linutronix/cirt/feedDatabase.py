#!/usr/bin/env python3

from sqlalchemy import create_engine, event
from sqlalchemy.schema import Table
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session
from sqlalchemy.orm.exc import NoResultFound
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy import (Column, ForeignKey, PrimaryKeyConstraint)
from sqlalchemy import (Integer, String, Boolean, Text,
                        LargeBinary, TIMESTAMP)
from contextlib import contextmanager
from junitparser import TestCase, TestSuite, JUnitXml, Skipped, \
                        Error, Failure, Element, Attr
from os import environ as env
from os.path import basename, dirname, join
import sys
from os import listdir
import time
from datetime import datetime
import argparse


Base = declarative_base()


class DBError(Exception):
    def __init__(self, message):
        Exception.__init__(self, message)


@contextmanager
def session_scope(session):
    try:
        yield session
        session.commit()
    except OperationalError as e:
        session.rollback()
        raise DBError("database commit failed: " + str(e))
    finally:
        session.close()


class HistData(Element):
    _tag = 'histdata'
    thread = Attr()
    latency = Attr()
    count = Attr()
    owner = Attr()


class ThreadData(Element):
    _tag = 'threaddata'
    thread = Attr()
    tmin = Attr()
    avg = Attr()
    tmax = Attr()
    owner = Attr()


def parse_junit(filename):
    xml = JUnitXml.fromfile(filename)
    system_out = ""
    system_err = ""
    props = {}
    hist_data = []
    thread_data = []

    for suite in xml:
        for case in suite:
            classname = case.classname
            name = case.name
            if (case.result is None):
                result = "pass"
            elif (type(case.result) is Failure):
                result = "failure"
            elif (type(case.result) is Error):
                result = "error"
            else:
                result = "error"
            if (case.system_out):
                system_out = case.system_out
            if (case.system_err):
                system_err = case.system_err

            if case.classname == "cyclictest":
                # handle the cyclictest junit xml files with custom elements
                # and properties
                for p in suite.properties():
                    props[p.name] = p.value

                for hist_element in case.iterchildren(HistData):
                    hist_data.append({
                        "thread": hist_element.thread,
                        "latency": hist_element.latency,
                        "count": hist_element.count,
                        "owner": hist_element.owner,
                        })
                for thread_element in case.iterchildren(ThreadData):
                    thread_data.append({
                        "thread": thread_element.thread,
                        "min": thread_element.tmin,
                        "avg": thread_element.avg,
                        "max": thread_element.tmax,
                        "owner": thread_element.owner
                        })

    return {
        "classname": classname,
        "name": name,
        "result": result,
        "system_out": system_out,
        "system_err": system_err,
        "props": props,
        "hist_data": hist_data,
        "thread_data": thread_data
        }


def extract_cyclictest_data(props):
    hist_data = []
    thread_data = []
    i = 0
    while "hist_data" + str(i) in props:
        data = props["hist_data" + str(i)].split(",")
        hist_data.append({
            "thread": data[0],
            "latency": data[1],
            "count": data[2],
            "owner": data[3]
            })
        i += 1
    i = 0
    while "thread_data" + str(i) in props:
        data = props["thread_data" + str(i)].split(",")
        thread_data.append({
            "thread": data[0],
            "min": data[1],
            "avg": data[2],
            "max": data[3],
            "owner": data[4]
            })
        i += 1
    return hist_data, thread_data


def get_current_time():
    today = datetime.utcnow()
    return today.strftime("%Y-%m-%d %H:%M:%S")


class Git (Base):
    __tablename__ = 'git'
    git_id = Column('id', Integer, primary_key=True)
    path = Column(String)
    public = Column(String)
    httprepo = Column(String)
    owner = Column(String)


class Tags (Base):
    __tablename__ = 'tags'
    tags_id = Column('id', Integer, primary_key=True)
    commit = Column(String)
    name = Column(String)
    git_id = Column(Integer, ForeignKey('git.id'))


class Cirtscheduler (Base):
    __tablename__ = 'cirtscheduler'
    cirtscheduler_id = Column('id', Integer, primary_key=True)
    branch = Column(String(80))
    timestamp = Column(TIMESTAMP)
    tags_id = Column(Integer, ForeignKey('tags.id'))
    pass_ = Column('pass', Boolean)
    owner = Column(Text)
    processing = Column(Boolean, default=False)


class Cirtbranch (Base):
    __tablename__ = 'cirtbranch'
    cirtbranch_id = Column('id', Integer, primary_key=True)
    testbranch = Column(String(80))
    commit = Column(String(40))
    cirtscheduler_id = Column(Integer, ForeignKey('cirtscheduler.id'))
    owner = Column(Text)


class Compiletest (Base):
    __tablename__ = 'compiletest'
    compiletest_id = Column('id', Integer, primary_key=True)
    custom_commit = Column(String(40))
    defconfig = Column(LargeBinary)
    overlay = Column(String(45))
    pass_ = Column('pass', Boolean)
    cirtscheduler_id = Column(Integer, ForeignKey('cirtscheduler.id'))
    configname = Column(String(80))
    arch = Column(String(40))
    buildcmd = Column(LargeBinary)
    owner = Column(Text)
    buildlog = Column(LargeBinary)


class Target (Base):
    __tablename__ = 'target'
    target_id = Column('id', Integer, primary_key=True)
    hostname = Column(String(80))
    shortdesc = Column(String(80))
    description = Column(LargeBinary)
    label_id = Column(Integer)
    owner = Column(Text)


class Boottest (Base):
    __tablename__ = 'boottest'
    boottest_id = Column('id', Integer, primary_key=True)
    cmdline = Column(String(1024))
    pass_ = Column('pass', Boolean)
    compiletest_id = Column(Integer, ForeignKey('compiletest.id'))
    target_id = Column(Integer, ForeignKey('target.id'))
    bootdate = Column(TIMESTAMP)
    owner = Column(Text)
    bootlog = Column(LargeBinary)


class Cyclictest (Base):
    __tablename__ = 'cyclictest'
    cyclictest_id = Column('id', Integer, primary_key=True)
    description = Column(String(80))
    duration = Column(Integer)
    interval = Column(Integer)
    min_ = Column('min', Integer)
    avg = Column(Integer)
    max_ = Column('max', Integer)
    boottest_id = Column(Integer, ForeignKey('boottest.id'))
    pass_ = Column('pass', Boolean)
    threshold = Column(Integer)
    testscript = Column(LargeBinary)
    owner = Column(Text)
    testlog = Column(LargeBinary)


class Histogram (Base):
    __tablename__ = 'histogram'
    histogram_id = Column('id', Integer, primary_key=True)
    thread = Column(Integer)
    latency = Column(Integer)
    count = Column(Integer)
    cyclictest_id = Column(Integer, ForeignKey('cyclictest.id'))
    owner = Column(Text)


class Minavgmax (Base):
    __tablename__ = 'minavgmax'
    __table_args__ = (
        PrimaryKeyConstraint('cyclictest_id', 'thread'),
        {},
    )
    thread = Column(Integer)
    cyclictest_id = Column(Integer, ForeignKey('cyclictest.id'))
    min_ = Column('min', Integer)
    avg = Column(Integer)
    max_ = Column('max', Integer)
    owner = Column(Text)
    PrimaryKeyConstraint('id', 'version_id', name='mytable_pk')


class CirtDB():
    def __init__(self, db_type, db_host, db_user, db_pass, db_name):
        db_string = "%s://%s:%s@%s/%s" % (db_type, db_user,
                                          db_pass, db_host, db_name)
        engine = create_engine(db_string)
        Base.metadata.create_all(engine)
        session = Session(engine)

        self.session = session

    def close(self):
        self.session.close()

    def submit_git(self, gitrepo, publicrepo, httprepo, entry_owner):
        try:
            with session_scope(self.session) as s:
                git_id = s.query(Git).\
                    filter(Git.path == gitrepo).\
                    filter(Git.public == publicrepo).\
                    filter(Git.httprepo == httprepo).\
                    filter(Git.owner == entry_owner).one().git_id
        except NoResultFound:
            # insert new git id to database
            new_git = Git(
                path=gitrepo,
                public=publicrepo,
                httprepo=httprepo,
                owner=entry_owner
                )
            with session_scope(self.session) as s:
                s.add(new_git)
                s.commit()
                git_id = new_git.git_id
        return git_id

    def submit_tags(self, commit, name, git_id):
        try:
            with session_scope(self.session) as s:
                tags_id = s.query(Tags).\
                    filter(Tags.commit == commit).\
                    filter(Tags.name == name).\
                    filter(Tags.git_id == git_id).one().tags_id
        except NoResultFound:
            # insert new tag to database
            new_tag = Tags(
                commit=commit,
                name=name,
                git_id=git_id
                )
            with session_scope(self.session) as s:
                s.add(new_tag)
                s.commit()
                tags_id = new_tag.tags_id
        return tags_id

    def submit_cirtscheduler(self, scheduler_id,
                             tags_id, branch, entry_owner):
        new_cirtscheduler = Cirtscheduler(
            cirtscheduler_id=scheduler_id,
            branch=branch.encode("UTF-8"),
            timestamp=get_current_time(),
            pass_=False,
            tags_id=tags_id,
            owner=entry_owner
            )
        with session_scope(self.session) as s:
            s.add(new_cirtscheduler)
            s.commit()
            return new_cirtscheduler.cirtscheduler_id

    def submit_cirtbranch(self, testbranch,
                          commit, scheduler_id, entry_owner):
        new_cirtbranch = Cirtbranch(
            cirtbranch_id=scheduler_id,
            testbranch=testbranch,
            commit=commit,
            cirtscheduler_id=scheduler_id,
            owner=entry_owner
            )
        with session_scope(self.session) as s:
            s.add(new_cirtbranch)
            s.commit()
            return new_cirtbranch.cirtbranch_id

    def submit_compiletest(self, compile_result,
                           scheduler_id, entry_owner, compile_script,
                           defconfig, overlay, arch, configname, system_out):
        new_compiletest = Compiletest(
            overlay=overlay,
            custom_commit="not set",
            defconfig=defconfig.encode("UTF-8"),
            cirtscheduler_id=scheduler_id,
            configname=configname,
            arch=arch,
            buildcmd=compile_script.encode("UTF-8"),
            pass_=(compile_result == "pass"),
            owner=entry_owner,
            buildlog=system_out.encode("UTF-8")
            )
        with session_scope(self.session) as s:
            s.add(new_compiletest)
            s.commit()
            return new_compiletest.compiletest_id

    def submit_boottest(self, boot_result,
                        compile_id, target_name, entry_owner, system_out,
                        cmdline):
        with session_scope(self.session) as s:
            target_id = s.query(Target).\
                filter(Target.hostname == target_name).one().target_id

        new_boottest = Boottest(
            cmdline=cmdline,
            target_id=target_id,
            compiletest_id=compile_id,
            bootdate=get_current_time(),
            pass_=(boot_result == "pass"),
            owner=entry_owner,
            bootlog=system_out.encode("utf-8")
            )
        with session_scope(self.session) as s:
            s.add(new_boottest)
            s.commit()
            return new_boottest.boottest_id

    def submit_cyclictest(self, cyclic_result,
                          boot_id, entry_owner, props, system_out):
        new_cyclictest = Cyclictest(
            description=props["description"],
            duration=props["duration"],
            interval=props["interval"],
            min_=props["min"],
            avg=props["avg"],
            max_=props["max"],
            boottest_id=boot_id,
            threshold=props["threshold"],
            testscript=props["testscript"].encode("utf-8"),
            pass_=(cyclic_result == "pass"),
            owner=entry_owner,
            testlog=system_out.encode("utf-8")
            )
        with session_scope(self.session) as s:
            s.add(new_cyclictest)
            s.commit()
            return new_cyclictest.cyclictest_id

    def submit_hist_data(self, hist_data, cyclic_id):
        with session_scope(self.session) as s:
            for hd in hist_data:
                new_histogram = Histogram(
                    thread=hd["thread"],
                    latency=hd["latency"],
                    count=hd["count"],
                    owner=hd["owner"],
                    cyclictest_id=cyclic_id
                    )
                s.add(new_histogram)

    def submit_thread_data(self, thread_data, cyclic_id):
        with session_scope(self.session) as s:
            for td in thread_data:
                new_minavgmax = Minavgmax(
                    thread=td["thread"],
                    min_=td["min"],
                    avg=td["avg"],
                    max_=td["max"],
                    owner=td["owner"],
                    cyclictest_id=cyclic_id
                    )
                s.add(new_minavgmax)

    def update_cirtscheduler(self, scheduler_id,
                             passed, first_run, last_run):
        with session_scope(self.session) as s:
            cirtscheduler = s.query(Cirtscheduler).\
                filter(Cirtscheduler.cirtscheduler_id == scheduler_id).one()
            if not first_run == "first_run":
                prev_pass = cirtscheduler["pass"]
                passed = passed and prev_pass

            cirtscheduler.pass_ = passed
            cirtscheduler.processing = last_run != "last_run"

# collect information from env and command line params
db_type = "postgresql"
db_host = env.get("DB_HOSTNAME")
db_user = env.get("DB_USER")
db_pass = env.get("DB_PASS")
db_name = "RT-Test"

parser = argparse.ArgumentParser()
parser.add_argument('path')
parser.add_argument('--buildnumber', required=True)
parser.add_argument('--workspace', required=True)
parser.add_argument('--overlay', required=True)
parser.add_argument('--config', required=True)
parser.add_argument('--git_branch', required=True)
parser.add_argument('--git_commit', required=True)
parser.add_argument('--gitrepo', required=True)
parser.add_argument('--publicrepo', required=True)
parser.add_argument('--httprepo', required=True)
parser.add_argument('--tags_commit', required=True)
parser.add_argument('--tags_name', required=True)
parser.add_argument('--branch', required=True)
parser.add_argument('--entryowner', required=True)
parser.add_argument('--first_run', required=True)
parser.add_argument('--last_run', required=True)
args = parser.parse_args()

scheduler_id = args.buildnumber
arch = dirname(args.config)
configname = basename(args.config)

all_tests_passed = False

result_path = join(args.workspace, args.path)
first_run = args.first_run
last_run = args.last_run

entry_owner = args.entryowner

db = CirtDB(db_type, db_host, db_user, db_pass, db_name)

if first_run == "first_run":
    git_id = db.submit_git(
        args.gitrepo, args.publicrepo, args.httprepo, entry_owner
        )
    tags_id = db.submit_tags(
        args.tags_commit, args.tags_name, git_id
        )
    scheduler_id = db.submit_cirtscheduler(
        scheduler_id, tags_id, args.branch, entry_owner
        )
    db.submit_cirtbranch(
        args.git_branch, args.git_commit, scheduler_id, entry_owner
        )

junit_res = parse_junit(join(result_path, "compile", "pyjutest.xml"))
with open(join(result_path, "compile", "compile-script.sh"), 'r') as fd:
    compile_script = fd.read()
with open(join(result_path, "build", "defconfig"), 'r') as fd:
    defconfig = fd.read()
compile_id = db.submit_compiletest(
    junit_res["result"], scheduler_id, entry_owner,
    compile_script, defconfig, args.overlay, arch, configname,
    junit_res["system_out"]
    )
all_tests_passed = (junit_res["result"] == "pass")
# iterate over all junit xml files for the different
# configurations of boot- and cyclictests
boottests = listdir(result_path)
for boottest in boottests:
    if boottest != "compile" and boottest != "build":
        junit_res = parse_junit(
            join(result_path, boottest, "boottest", "pyjutest.xml")
                )
        with open(join(result_path, boottest,
                  "cmdline"), 'r') as fd:
            cmdline = fd.read()
        boot_id = db.submit_boottest(
            junit_res["result"],
            compile_id, boottest, entry_owner, junit_res["system_out"],
            cmdline
            )
        all_tests_passed = all_tests_passed and \
            (junit_res["result"] == "pass")
        cyclictests = listdir(
            join(result_path, boottest, "cyclictest", boottest)
                )
        for cyclic in cyclictests:
            cyclic_path = join(result_path, boottest, "cyclictest",
                               boottest, cyclic)
            junit_res = parse_junit(join(cyclic_path, "pyjutest.xml"))
            cyclic_id = db.submit_cyclictest(
                junit_res["result"],
                boot_id, entry_owner,
                junit_res["props"],
                junit_res["system_out"]
                )
            all_tests_passed = all_tests_passed and \
                (junit_res["result"] == "pass")
            hist_data, thread_data = extract_cyclictest_data(
                junit_res["props"]
                )
            db.submit_hist_data(
                hist_data,
                cyclic_id
                )
            db.submit_thread_data(
                thread_data,
                cyclic_id
                )

db.update_cirtscheduler(
    scheduler_id,
    all_tests_passed,
    first_run,
    last_run
    )

db.close()
