#! /bin/bash

ADB=$(which adb)

SERIAL=emulator-5554

if [[ "x$ADB" == "x"  ]];
then
    echo "No adb in $PATH"
    exit 1
fi

DIR=$(pushd "$(dirname "$BASH_SOURCE[0]")" > /dev/null && pwd && popd > /dev/null )

$ADB -s $SERIAL ls /sdcard/
if [[ $? != 0 ]]
then
    echo "Device $SERIAL is offline or there is no /sdcard/."
    exit
fi

install_lib() {
 echo "Push..."
 $ADB -s $SERIAL push "${DIR}/$LIB_FILE" /sdcard/$LIB_FILE
 echo "Backup..."
 $ADB -s $SERIAL shell su root cp /system/lib/$LIB_FILE /sdcard/$LIB_FILE.backup
 echo "Pull backup..."
 $ADB -s $SERIAL pull /sdcard/$LIB_FILE.backup ${DIR}
 echo "Copy..."
 $ADB -s $SERIAL shell su root cp /sdcard/$LIB_FILE /system/lib/$LIB_FILE
 echo "Chmod..."
 $ADB -s $SERIAL shell su root chmod 644 /system/lib/$LIB_FILE
 echo "Chown..."
 $ADB -s $SERIAL shell su root chown root:root /system/lib/$LIB_FILE
}


$ADB -s $SERIAL root
$ADB -s $SERIAL remount
$ADB -s $SERIAL shell su root mount -o remount,rw /system

LIBS=(libart.so libart-compiler.so)
for LIB in ${LIBS[@]}
do
    LIB_FILE=$LIB
    install_lib
done

$ADB -s $SERIAL shell su root reboot
