#!/bin/bash

LIST="$1"
NAME="$2"
PROP="$3"

if [ x"$2" == x ]
then
    echo "usage: $0 <listfile> <variable> <propertyfile>"
    exit 1
fi

if [ ! -f $LIST ]
then
	echo "$NAME=\"\"" >> $PROP
	exit 0
fi

# ignore empty lines and everything after "#".
# concat each line seperated by space into variable $NAME
# listfile:
# A
# B
# C
# NAME = "var"
# propertyfile:
# var = A B C

awk -vV="$NAME" \
    'BEGIN{A=""}{gsub ("#.*", ""); if ($0) A=A" "$1}END{print V" = "A}' \
    $LIST >> $PROP
