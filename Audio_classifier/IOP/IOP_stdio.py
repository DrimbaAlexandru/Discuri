import sys
import struct

#----------------------------------------------
# Global constants
#
# Special characters
#----------------------------------------------
from io import SEEK_END

DLE_BYTE = 0x10
STX_BYTE = 0x02
ETX_BYTE = 0x03

#----------------------------------------------
# Parse states
#----------------------------------------------
iop_PARSE_STATE_STX = 0
iop_PARSE_STATE_DATA = 1
iop_PARSE_STATE_DLE = 2
iop_PARSE_STATE_ETX = 3

iop_PARSE_STATUS_CONTINUE = 0
iop_PARSE_STATUS_ACK = 1
iop_PARSE_STATUS_ERROR = 2

#----------------------------------------------
# Other constants
#----------------------------------------------
IOP_MSG_DLE_BYTES_SIZE = 4
IOP_MSG_HDR_SIZE = 4
IOP_MSG_MAX_DATA_LENGTH = 2**13 - 1
IOP_MSG_MAX_LENGTH = IOP_MSG_MAX_DATA_LENGTH * 2 + IOP_MSG_DLE_BYTES_SIZE + IOP_MSG_HDR_SIZE

#----------------------------------------------
# Local variables
#----------------------------------------------
_parse_state = iop_PARSE_STATE_ETX
_parse_status = iop_PARSE_STATUS_CONTINUE
_rx_buffer = b''
_parse_index = 0
_decoded_buffer = bytearray( b'' )

rx_idx = 0

#----------------------------------------------
# Procedures
#----------------------------------------------
def IOP_get_bytes():
    global _rx_buffer
    global rx_idx

    rxd_bytes = b''

    if( sys.stdin.buffer.seekable() ):
        crnt_pos = sys.stdin.buffer.tell()
        sys.stdin.buffer.seek(0,SEEK_END)
        bytes_avail = sys.stdin.buffer.tell() - crnt_pos
        sys.stdin.buffer.seek(crnt_pos)
        if( bytes_avail > 0 ):
            #print( "Reading %u bytes " % bytes_avail, file = sys.stderr, flush=True )
            rxd_bytes = sys.stdin.buffer.read1( bytes_avail )
            #print( "Read %u bytes " % len( rxd_bytes ), file = sys.stderr, flush=True )
    # rx_tbl = [ # b'\x10\x02\x07\x01\x00\x00\x10\x03',                     # request classifier info
    #            # b'\x10\x02\x01\x01\x04\x00\x04\x00\x00\x00\x10\x03',     # audio start rx
    #            # b'\x10\x02\x01\x02\x0A\x00\x00\x00\x00\x00\x02\x00\x01\x00\x02\x00\x10\x03',     # audio data 1
    #            # b'\x10\x02\x01\x02\x0A\x00\x02\x00\x00\x00\x02\x00\x03\x00\x04\x00\x10\x03',     # audio data 2
    #            b'\x10\x02\x08\xFF\x00\x00\x10\x03'
    #          ]
    # if rx_idx < len( rx_tbl ):
    #     rxd_bytes = rx_tbl[ rx_idx ]
    # else:
    #     rxd_bytes = b''
    # rx_idx += 1

    if rxd_bytes is None:
        raise EOFError( "Stream closed" )

    _rx_buffer = _rx_buffer + rxd_bytes
    return len( rxd_bytes )


def IOP_get_frame():
    global _parse_state, _parse_status, _rx_buffer, _parse_index, _decoded_buffer

    ret_val = None

    rxd_bytes_cnt = len( _rx_buffer )
    while( _parse_index < rxd_bytes_cnt and ret_val is None ):
        parsed_byte = iop_parse_byte( _rx_buffer[ _parse_index ] )
        if( parsed_byte is not None ):
            _decoded_buffer.append( parsed_byte )

        if( _parse_state == iop_PARSE_STATE_ETX and _parse_status != iop_PARSE_STATUS_CONTINUE ):
            if( _parse_state == iop_PARSE_STATE_ETX and _parse_status == iop_PARSE_STATUS_ACK ):
                if( len( _decoded_buffer ) < 4 ):
                    #print( "Invalid frame", file=sys.stderr )
                    pass
                else:
                    msg_id = _decoded_buffer[0:1]
                    msg_subid = _decoded_buffer[1:2]
                    msg_size = _decoded_buffer[2:4]
                    msg_data = _decoded_buffer[4:]

                    id = struct.unpack( '<B', msg_id )[ 0 ]
                    subid = struct.unpack( '<B', msg_subid )[ 0 ]
                    size = struct.unpack( '<H', msg_size )[ 0 ]

                    if( len( msg_data ) != size ):
                        #print( "Message sizes not matching", file=sys.stderr )
                        pass
                    else:
                        ret_val = ( id, subid, size, msg_data )

            if( _parse_state == iop_PARSE_STATE_ETX and _parse_status == iop_PARSE_STATUS_ERROR ):
                #print( "Parse error", file=sys.stderr )
                pass

            # Discard the processed bytes
            _rx_buffer = _rx_buffer[_parse_index + 1:]
            _parse_index = 0
            _parse_status = iop_PARSE_STATUS_CONTINUE
            rxd_bytes_cnt = len( _rx_buffer )
            _decoded_buffer = bytearray( b'' )

        else:
            _parse_index = _parse_index + 1

    return ret_val


def iop_parse_byte( byte ):
    global _parse_state, _parse_status
    parsed_byte = None

    if( _parse_state == iop_PARSE_STATE_ETX ):
        # Expect the beginning of a new frame ( DLE )
        if( byte == DLE_BYTE ):
            _parse_state = iop_PARSE_STATE_STX
            _parse_status = iop_PARSE_STATUS_CONTINUE
        else:
            _parse_state = iop_PARSE_STATE_ETX
            _parse_status = iop_PARSE_STATUS_ERROR

    elif( _parse_state == iop_PARSE_STATE_STX ):
        # Expect the STX byte
        if( byte == STX_BYTE ):
            _parse_state = iop_PARSE_STATE_DATA
        else:
            _parse_state = iop_PARSE_STATE_ETX
            _parse_status = iop_PARSE_STATUS_ERROR

    elif( _parse_state == iop_PARSE_STATE_DATA ):
        # DLE bytes are to be treated specially
        if( byte == DLE_BYTE ):
            _parse_state = iop_PARSE_STATE_DLE
        else:
            parsed_byte = byte

    elif( _parse_state == iop_PARSE_STATE_DLE ):
        # Process DLE stuffing and ETX bytes. Otherwise, message is invalid
        if( byte == DLE_BYTE ):
            _parse_state = iop_PARSE_STATE_DATA
            parsed_byte = byte
        elif( byte == ETX_BYTE ):
            _parse_state = iop_PARSE_STATE_ETX
            _parse_status = iop_PARSE_STATUS_ACK
        else:
            _parse_state = iop_PARSE_STATE_ETX
            _parse_status = iop_PARSE_STATUS_ERROR
    else:
        # Unexpected state
        _parse_state = iop_PARSE_STATE_ETX
        _parse_status = iop_PARSE_STATUS_ERROR

    return parsed_byte


def IOP_put_bytes( raw_bytes ):
    byte_cnt = sys.stdout.buffer.write( raw_bytes )
    sys.stdout.flush()
    return byte_cnt

def IOP_put_frame( id, subid, data ):
    bytes = bytearray( b'' )

    if( data is None ):
        data = b''

    if( isinstance( data, str ) ):
        data = data.encode("ascii")
        
    bytes = bytes + struct.pack( '<B', DLE_BYTE )
    bytes = bytes + struct.pack( '<B', STX_BYTE )

    #print( "Txing message %u %u %u" % (id, subid, len(data)), file = sys.stderr, flush=True )

    data = struct.pack( '<B', id ) + struct.pack( '<B', subid ) + struct.pack( '<H', len( data ) ) + data

    # DLE stuffing
    data = data.replace( b'\x10', b'\x10\x10' )

    bytes = bytes + data
    bytes = bytes + struct.pack( '<B', DLE_BYTE )
    bytes = bytes + struct.pack( '<B', ETX_BYTE )

    bytes_written = IOP_put_bytes( bytes )

    #print( "Txd message %u %u %u" % (id, subid, len(data)), file = sys.stderr, flush=True )

    if bytes_written == len( bytes ):
        return bytes_written
    elif bytes_written is not None:
        #print( "Message not fully written", file = sys.stderr )
        raise Exception("Message not fully written")
    return len( bytes )





#
# int_size = 4
# float_size = 4
# max_chunk = 8 * 1024
#
# def send_errors( errors ):
#     string = ''
#     if( len( errors ) > 0 ):
#         for error in errors:
#             string += error + '\n'
#     else:
#         string = 'ok'
#     pack = struct.pack( '> ' + str( len( string ) ) + 's', string )
#     sys.stderr.write( struct.pack( '>i', len( pack ) ) )
#     sys.stderr.write( pack )
#
# def read_float_array():
#     recv = sys.stdin.read( int_size * 1 )
#     #print >> sys.stderr, len( recv ), recv
#     array_len = struct.unpack( '>i', recv )[ 0 ]
#     array = []
#     for i in xrange( 0, array_len ):
#         recv = sys.stdin.read( float_size * 1 )
#         value = struct.unpack( '>f', recv )[ 0 ]
#         array.append( value )
#     return ( array_len, array )
#
# def print_float_array( array_len, array ):
#     sys.stdout.write( struct.pack( '>i', array_len ) )
#     for i in xrange( 0, array_len ):
#         sys.stdout.write( struct.pack( '>f', array[ i ] ) )
#
# err_msgs = []
#
# try:
#     import traceback
#     import sys
#     import struct
#     import pickle
#     import os
#     from sklearn.neural_network.multilayer_perceptron import MLPClassifier
#     from sklearn.preprocessing.data import StandardScaler
# except Exception as e:
#     err_msgs.append( traceback.format_exc() )
#     send_errors( err_msgs )
#     sys.exit( 1 )
#
# #Check for command line arguments
# if( len( sys.argv ) < 3 ):
#     err_msgs.append( 'Expected 2 command line arguments, got ' + str( len( sys.argv ) - 1 ) )
#     send_errors( err_msgs )
#     sys.exit( 2 )
#
# #Check for serialized objects' loading
# try:
#     mlp_f = open( sys.argv[ 1 ], "rb" )
#     scaler_f = open( sys.argv[ 2 ], "rb" )
#
#     mlp = pickle.load( mlp_f )
#     scaler = pickle.load( scaler_f )
#     inputs_size = mlp.coefs_[ 0 ].__len__()
# except Exception as e:
#     err_msgs.append( traceback.format_exc() )
#     send_errors( err_msgs )
#     sys.exit( 3 )
#
# send_errors( err_msgs )
#
# sys.stderr = open("C:\Users\Alex\Desktop\python_stderr.txt", "w+" )
# #begin loop
# while True:
#     ( n, samples ) = read_float_array()
#     if( n == 0 ):
#         break
#     i = 0
#     predictions = []
#     while( i < n - inputs_size + 1 ):
#         temp_len = min( n - inputs_size + 1 - i, max_chunk )
#         inputs = []
#         for j in xrange( i, i + temp_len ):
#             inputs.append( samples[ j : j + inputs_size ] )
#         inputs = scaler.transform( inputs )
#         predictions_chunk = mlp.predict_proba( inputs )[:,1]
#         #print >> sys.stderr, predictions_chunk, type( predictions_chunk )
#         predictions.extend( predictions_chunk )
#         i += temp_len
#     print_float_array( predictions.__len__(), predictions )