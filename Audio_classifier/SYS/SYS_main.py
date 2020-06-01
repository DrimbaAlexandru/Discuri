import sys
import os
import logging
import time

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'  # FATAL
logging.getLogger('tensorflow').setLevel(logging.FATAL)

# Redirect stderr to a file, because the client does not read from it
sys.stderr = open( "log.txt", "w" )

from SYS.SYS_time import *
from IOP.IOP_audio_classifier import *

#------------------------------------------
# Local constants
#------------------------------------------
SYS_TSK_PERIOD_MS = 1

#------------------------------------------
# Global variables
#------------------------------------------
SYS_run_loop = True

def SYS_main_stop_loop():
    global SYS_run_loop
    SYS_run_loop = False

class SYS_main:
    #------------------------------------------
    # Local variables
    #------------------------------------------
    _s_last_task_tick = 0

    #------------------------------------------
    # Procedures
    #------------------------------------------
    def __init__( self ):
        self.audio_tsk = IOP_audio_classifier()
        global SYS_run_loop

        SYS_run_loop = True

    def run( self ):
        try:
            while SYS_run_loop:
                self._s_last_task_tick = SYS_get_time_ms()

                #----------------------------------
                # Run periodic audio I/O
                #----------------------------------
                self.audio_tsk.audio_input()
                self.audio_tsk.audio_periodic_output()

                # Wait until the next tick
                time_left = SYS_TSK_PERIOD_MS - ( SYS_get_time_ms() - self._s_last_task_tick )
                if( time_left > 0 ):
                    time.sleep( time_left / 1000.0 )
            time.sleep( 1 )
        except BaseException as e:
            pass
            #traceback.print_exc( file = sys.stderr )
