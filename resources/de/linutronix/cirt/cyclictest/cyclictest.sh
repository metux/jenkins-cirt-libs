#! /bin/bash
# Exit bash script on error:
set -e

echo "${LOADGEN:=true}" > loadgen.txt

cat << _EOF_ > histogram.sh
set -e

${LOADGEN:=true} &
sudo cyclictest -q -m -Sp99 -D${DURATION} -i${INTERVAL} -h${LIMIT} -b${LIMIT} --notrace 2> >(tee histogram.log >&2) | tee histogram.dat
_EOF_

bash histogram.sh
