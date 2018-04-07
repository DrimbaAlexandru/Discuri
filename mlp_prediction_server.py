from sklearn.neural_network import MLPClassifier
from sklearn.preprocessing import StandardScaler
import sys
import struct
import pickle

int_size = 4
double_size = 8
max_chunk = 8 * 1024

def send_errors( errors ):
    if( len( errors ) > 0 ):
        for error in errors:
             string += error + '\n'
    else:
        string = 'ok'
    print >> sys.stderr, string

def read_double_array():
    ( array_len ) = struct.unpack( '>i', sys.stdin.read( int_size * 1 ) )
    array = []
    for i in xrange( 0, array_len ):
        ( value ) = struct.unpack( '>d', sys.stdin.read( double_size * 1 ) )
        array.append( value )
    return ( array_len, array )
    
def print_double_array( array_len, array )
    sys.stdout.write( struct.pack( '>i', array_len ) )
    for i in xrange( 0, array_len ):
        sys.stdout.write( struct.pack( '>d', array[ i ] ) )

err_msgs = []

#Check for command line arguments
if( len( sys.argv ) < 3 ):
    err_msgs.append( 'Expected 2 command line arguments, got ' + str( len( sys.argv ) - 1 ) )
    send_errors( err_msgs )
    sys.exit( 1 )

mlp_f = open( sys.argv[ 1 ], "r" )
scaler_f = open( sys.argv[ 2 ], "r" )
inputs_size = mlp.coefs_[ 0 ].__len__()

#Check for serialized objects' loading
try:
    mlp = pickle.load( mlp_f )
    scaler = pickle.load( scaler_f )
except Exception as e:
    err_msgs.append( str( e ) )
    send_errors( err_msgs )
    sys.exit( 2 )

#begin loop
while True:
    ( n, samples ) = read_double_array()
    if( n == 0 ):
        break
    i = 0
    predictions = []
    while( i < n - inputs_size + 1 ):
        temp_len = ( n - inputs_size + 1 - i, max_chunk )
        inputs = []
        for j in xrange( i, i + temp_len ):
            inputs.append( samples[ j : j + inputs_size ] )
        inputs = scaler.transform( inputs )
        predictions_chunk = mlp.predict( inputs )[ 1 ]
        predictions.extend( predictions_chunk )
        i += temp_len
    print_double_array( predictions.__len__(), predictions )