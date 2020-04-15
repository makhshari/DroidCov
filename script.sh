
#!/bin/bash

APP_INDEX_START=5
APP_INDEX_END=5
TEST_DURATION=60 #Minutes
APP_ITERATION=2

EMULATOR_DIR="/Users/amirosein/Library/Android/sdk/emulator/emulator"
EMULATOR_NAME="@Nexus_5_API_23"
APPS_DIR="/Users/amirosein/Desktop/mobproj/apps/"
MINITRACE_DIR="/Users/amirosein/Desktop/mobproj/coverageTools/miniTrace/android-mt-cmd/minitrace.py"
PARSER_DIR="/Users/amirosein/Desktop/mobproj/coverageTools/miniTrace/parser/run.py"

SAPIENZ_DIR="/Users/amirosein/Desktop/mobproj/sapienz/main.py"
APE_DIR="/Users/amirosein/Desktop/mobproj/ape-bin/ape.py"

echo "Automated testing started"
APP_INDEX=$APP_INDEX_START
while [ $APP_INDEX -le $APP_INDEX_END ]
do
# if [[ $APP_INDEX != 5 && $APP_INDEX != 13 && $APP_INDEX != 15  && $APP_INDEX != 21   ]];
# then
#   echo "Invalid App"
#   APP_INDEX=$((APP_INDEX + 1))
#   continue 1
# fi
APP_PATH=$(echo $APPS_DIR$APP_INDEX/*.apk) #like apps/1/x.apk
APP_DIR="${APP_PATH%/*.apk}"  #like apps/1/
basename "$APP_PATH"
APP_NAME="$(basename -- $APP_PATH)"
APP_NAME=${APP_NAME%.apk} #The package name


ITERATION=1
COV_RESULT_FILE=$(echo $APPS_DIR$APP_INDEX/Mon$APP_INDEX.txt)

while [ $ITERATION -le $APP_ITERATION ]
do
  echo "\nGetting the coverage for app index: $APP_INDEX ITERATION: $ITERATION\n"
  mkdir $APP_DIR/MONDAT
  mkdir $APP_DIR/MONDAT/IT$ITERATION
  # BOOTING EMULATOR
  $EMULATOR_DIR $EMULATOR_NAME -writable-system  -no-snapshot > /dev/null 2>&1 &
  echo "\nWait for the emulator to boot\n"
  A=$(adb shell getprop init.svc.bootanim | tr -d '\r')
  while [ "$A" != "stopped" ]; do
          sleep 2
          A=$(adb shell getprop init.svc.bootanim | tr -d '\r')
  done
  echo "\nemulator booted successfully\n"
  sleep 5s 

  # EXTRACTING AND INSTALLING APP 
  echo "Start to install the app"
  if test -f "$APP_PATH"
  then   
    echo "installing $APP_NAME"
    adb install $APP_PATH
  # if adb install $APP_PATH | grep -q 'INSTALL_FAILED_ALREADY_EXISTS'; then
  #   echo "\n$APP_NAME is already installed\n"
  # else 
  #   echo "\n$APP_NAME installed\n"
  # fi
 
  # HARVESTING THE COVERAGE TOOL

  echo "\n___________________________________________________running enable\n"
  export SERIAL=emulator-5554
  echo $(python2 $MINITRACE_DIR enable $APP_NAME)

  sleep 30s

  echo "\n___________________________________________________running enable\n"
  export SERIAL=emulator-5554
  echo $(python2 $MINITRACE_DIR enable $APP_NAME)

  echo "\n___________________________________________________running Harvest\n"
  echo $(python2 $MINITRACE_DIR harvest $APP_NAME)



  # RUNNING THE TEST TOOL
  #MONKEY
  echo "\n___________________________________________________running Monkey\n"
  adb shell monkey --ignore-crashes --ignore-timeouts --ignore-security-exceptions -p $APP_NAME -v 99000000 > /dev/null 2>&1 &

  #SAPIENZ
  # echo "\n___________________________________________________running Sapienz\n"
  # sudo python $SAPIENZ_DIR $APP_PATH > /dev/null 2>&1 &

  #APE
  # echo "\n___________________________________________________running APE\n"
  # $APE_DIR -p $APP_NAME --running-minutes $TEST_DURATION --ape sata > /dev/null 2>&1 &


  sleep 60s
  # HARVESTING THE TEST EVERY MINUTE
  S=0
  while [ $S -le $TEST_DURATION ]
  do  
    echo $(python2 $MINITRACE_DIR harvest $APP_NAME)
    sleep 60s  
    S=$((S + 1))
    echo "$S"
  done
  echo "donenoeoeneoneoneneneoneeedddoneonddoneonddneondoend"

  # TEST DONE. EXTRACTING COVERAGE RESULT
  echo "\n___________________________________________________running Disable\n"
  echo $(python2 $MINITRACE_DIR disable $APP_NAME)

  echo "\n___________________________________________________Coppying from sdcard to app dir\n"
  echo $(adb pull /sdcard/$APP_NAME $APP_DIR/MONDAT/IT$ITERATION )

  echo "\n___________________________________________________Parsing the .dat file\n"
  echo $(python2 $PARSER_DIR -apk $APP_PATH -cov $APP_DIR/MONDAT/IT$ITERATION/$APP_NAME/*.dat >> $COV_RESULT_FILE)

# adb shell "rm -rf /sdcard/sata*"
adb shell pm clear $APP_NAME
adb uninstall $APP_NAME 
  # KILLING EMULATOR
  echo $(adb -s emulator-5554 emu kill)	
  sleep 10m 

  else 
  echo "$APP_PATH Does not exist"   
  fi
  ITERATION=$((ITERATION + 1))
done
sleep 30s 
APP_INDEX=$((APP_INDEX + 1))
done






