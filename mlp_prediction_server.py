int_size = 4
double_size = 8
max_chunk = 8 * 1024

def send_errors( errors ):
    string = ''
    if( len( errors ) > 0 ):
        for error in errors:
             string += error + '\n'
    else:
        string = 'ok'
    pack = struct.pack( '> ' + str( len( string ) ) + 's', string )
    sys.stderr.write( struct.pack( '>i', len( pack ) ) )
    sys.stderr.write( pack )

def read_double_array():
    recv = sys.stdin.read( int_size * 1 )
    print >> sys.stderr, len( recv ), recv
    array_len = struct.unpack( '>i', recv )[ 0 ]
    array = []
    for i in xrange( 0, array_len ):
        recv = sys.stdin.read( double_size * 1 )
        value = struct.unpack( '>d', recv )[ 0 ]
        array.append( value )
    return ( array_len, array )
    
def print_double_array( array_len, array ):
    sys.stdout.write( struct.pack( '>i', array_len ) )
    for i in xrange( 0, array_len ):
        sys.stdout.write( struct.pack( '>d', array[ i ] ) )

err_msgs = []

try:
    from sklearn.neural_network.multilayer_perceptron import MLPClassifier
    from sklearn.preprocessing.data import StandardScaler
    import sys
    import struct
    import pickle
    import traceback
    import os
except Exception as e:
    err_msgs.append( traceback.format_exc() )
    send_errors( err_msgs )
    sys.exit( 1 )

#Check for command line arguments
if( len( sys.argv ) < 3 ):
    err_msgs.append( 'Expected 2 command line arguments, got ' + str( len( sys.argv ) - 1 ) )
    send_errors( err_msgs )
    sys.exit( 2 )

#Check for serialized objects' loading
try:
    mlp_f = open( sys.argv[ 1 ], "rb" )
    scaler_f = open( sys.argv[ 2 ], "rb" )

    mlp = pickle.load( mlp_f )
    scaler = pickle.load( scaler_f )
    inputs_size = mlp.coefs_[ 0 ].__len__()
except Exception as e:
    err_msgs.append( traceback.format_exc() )
    send_errors( err_msgs )
    sys.exit( 3 )

send_errors( err_msgs )

sys.stderr = open("C:\Users\Alex\Desktop\python_stderr.txt", "w+" )
#begin loop
while True:
    ( n, samples ) = read_double_array()
    if( n == 0 ):
        break
    i = 0
    predictions = []
    while( i < n - inputs_size + 1 ):
        temp_len = min( n - inputs_size + 1 - i, max_chunk )
        inputs = []
        for j in xrange( i, i + temp_len ):
            inputs.append( samples[ j : j + inputs_size ] )
        inputs = scaler.transform( inputs )
        predictions_chunk = mlp.predict_proba( inputs )[:,1]
        print >> sys.stderr, predictions_chunk, type( predictions_chunk )
        predictions.extend( predictions_chunk )
        i += temp_len
    print_double_array( predictions.__len__(), predictions )