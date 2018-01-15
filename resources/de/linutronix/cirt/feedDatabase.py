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


# collect information from env and command line params
db_type = "postgresql"
db_host = env.get("DB_HOSTNAME")
db_user = env.get("DB_USER")
db_pass = env.get("DB_PASS")
db_name = "RT-Test"

scheduler_id = env.get("BUILD_NUMBER")
workspace = env.get("WORKSPACE")
testbranch = env.get("GIT_BRANCH")
commit = env.get("GIT_COMMIT")
gitrepo = env.get("GITREPO")
publicrepo = env.get("PUBLICREPO")
httprepo = env.get("HTTPREPO")
tags_commit = env.get("TAGS_COMMIT")
tags_name = env.get("TAGS_NAME")
branch = env.get("BRANCH")

result_path = join(workspace, sys.argv[1])
first_run = sys.argv[2]
last_run = sys.argv[3]

entry_owner = env.get("ENTRYOWNER")

db = CirtDB(db_type, db_host, db_user, db_pass, db_name)

if first_run == "first_run":
    git_id = db.submit_git(
        gitrepo, publicrepo, httprepo, entry_owner
        )
    tags_id = db.submit_tags(
        tags_commit, tags_name, git_id
        )
    scheduler_id = db.submit_cirtscheduler(
        scheduler_id, tags_id, branch, entry_owner
        )
    db.submit_cirtbranch(
        testbranch, commit, scheduler_id, entry_owner
        )

db.close()
