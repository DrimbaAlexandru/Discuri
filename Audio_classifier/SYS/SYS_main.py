import traceback
import time

from SYS.SYS_time import *
from IOP.IOP_audio_classifier import *
from IOP.IOP_stdio import *

#------------------------------------------
# Local constants
#------------------------------------------
SYS_TSK_PERIOD_MS = 25

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

    def run( self ):
        while True:
            try:
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

            except BaseException as e:
                traceback.print_exc()
                IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, traceback.format_exc() )
