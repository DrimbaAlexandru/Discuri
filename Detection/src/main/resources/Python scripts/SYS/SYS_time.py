import time

#----------------------------------------------
# Local variables
#----------------------------------------------
_init_time = int(round(time.time() * 1000))


def SYS_get_time_ms():
    return int(round(time.time() * 1000)) - _init_time
