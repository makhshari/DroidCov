#! /usr/bin/python

import os, sys, traceback, subprocess

DIR=os.path.abspath(os.path.dirname(__file__))

ADB=os.getenv('ADB', 'adb')

APE_ROOT='/data/local/tmp/'
APE_JAR=APE_ROOT + 'minitrace-cmd.jar'

APE_MAIN='org.javelus.minitrace.android.config.Main'

APP_PROCESS='/system/bin/app_process'

SERIAL=os.getenv('SERIAL')

if SERIAL:
    BASE_CMD=[ADB, '-s', SERIAL, 'shell', 'CLASSPATH=' + APE_JAR, APP_PROCESS, APE_ROOT, APE_MAIN]
else:
    BASE_CMD=[ADB, '-d', 'shell', 'CLASSPATH=' + APE_JAR, APP_PROCESS, APE_ROOT, APE_MAIN]

def install():
    if SERIAL:
        run_cmd([ADB, '-s', SERIAL, 'push', os.path.join(DIR, 'minitrace-cmd.jar'), APE_ROOT])
    else:
        run_cmd([ADB, '-d', 'push', os.path.join(DIR, 'minitrace-cmd.jar'), APE_ROOT])

def run_cmd(*args):
    print('Run cmd: ' + (' '.join(*args)))
    subprocess.check_call(*args)

def run_ape(args):
    run_cmd(BASE_CMD + list(args))

if __name__ == '__main__':
    try:
        install()
        run_ape(sys.argv[1:])
    except:
        traceback.print_exc()

