from time import process_time_ns

#----------------------------------------------
# Local variables
#----------------------------------------------
_init_time = process_time_ns()


def SYS_get_time_ms():
    return ( process_time_ns() - _init_time ) // 1000
