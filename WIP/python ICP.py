import struct
import sys
import time

(d1,d2,d3,d4) = struct.unpack( '>dddd', sys.stdin.read( 8 * 4 ) )

#print d1, d2, d3, d4
buf = struct.pack('>dddd',9,7,5,3)
#sys.stdout.write(': ' + ( sys.stdin.read(4) ) )
print buf
#time.sleep(5)