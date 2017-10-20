#!/usr/bin/python
#
# This script creates architecture dependent .properties files for compiletest environment settings
# Settings of the main compile file (compile/env/compile) are overwritten by architecture dependent
# settings (compile/env/ARCH). This results in a architecture dependent properties file, even if it
# will be empty (compile/env/ARCH.properties)

import sys
import re
import fileinput
import os
from os import environ as env
from shutil import copyfile

def touch(fname):
    try:
        os.utime(fname, None)
    except:
        open(fname, 'a').close()


def create_properties(arch_file, properties):
    with open(arch_file, 'r') as a:
        for arch_line in a:
            var = arch_line.split("=")[0]
            re_var = re.compile("^"+var)

            replace = 0

            # make sure when printing line, rstrip() is used
            # to remove newline
            for line in fileinput.input(properties, inplace=1):
                if re_var.search(line):
                    line = line.replace(line.rstrip(), arch_line.rstrip())
                    replace = 1
                    # write to file
                print line.rstrip()

            fileinput.close()

            if replace == 0:
                with open(properties, "a") as p:
                    p.write(arch_line.rstrip())



# extract all architectures from environment variable CONFIGS
configs = env.get("CONFIGS").split(" ")
archs = [ i.split('/')[0] for i in configs ]
archs = sorted(set(archs))

glob = "compile/env/compile"

# Touch global file
touch(glob)

for arch in archs:
    if not arch.strip():
        continue
    arch_file = "compile/env/"+arch.strip()
    properties = "compile/env/"+arch.strip()+".properties"

    copyfile(glob, properties)

    if os.path.exists(arch_file):
        create_properties(arch_file, properties)
