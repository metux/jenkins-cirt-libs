#! /bin/bash

# Exit bash script on error:
set -e

# clear the directory where test images, dtbs and initrds are stored
rm -rf /boot/jenkins/*

# Have a look for the linux image that should be tested (the package
# was copied by Build Step before); if package is not available exit
# with non zero exit code, else install package

export DEBPKG=$(find compile/ -name "*linux-image*-${SCHEDULER_ID}-*.deb")
export DEB=$(basename $DEBPKG)

if [ x = x"$DEBPKG" ]
then
	echo "No Kernel package found"
	exit 1
fi

sudo dpkg -i $DEBPKG

# Copy linux image and initrd to /boot/jenkins directory. If
# /boot/jenkins/bzImage exists, a kexec is executed with the kernel
# that should be tested when leaving a runlevel
# (see /etc/rc.local on target).
find /boot/ -maxdepth 1 -regextype posix-extended -regex '^/boot/vmlinuz.*-'${SCHEDULER_ID}'(-.*|$)' -exec cp {} /boot/jenkins/bzImage \;
if [ -f /boot/initrd.img-*-${SCHEDULER_ID} ]
then
	cp /boot/initrd.img-*-${SCHEDULER_ID} /boot/jenkins/initrd
fi

# Purge the debian package. In case of a reboot the default (and
# properly working kernel version is booted)
sudo dpkg --purge ${DEB%%_*}

# Unpack devicetrees
find . -name dtbs-${SCHEDULER_ID}.tar.xz -exec tar xJf {} -C /boot/jenkins \;
