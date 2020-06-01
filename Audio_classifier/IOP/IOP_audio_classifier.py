import traceback

from IOP.IOP_stdio import *
from SYS.SYS_time import *
import SYS.SYS_main
from AI.AI_audio_classifier import *

#----------------------------------------------
# Global constants
#
# Message IDs and Sub-IDs
#----------------------------------------------
IOP_MSG_ID_AUDIO_RX = 1
IOP_MSG_SUBID_AUDIO_RX_START = 1
IOP_MSG_SUBID_AUDIO_RX_DATA = 2

IOP_MSG_ID_AUDIO_TX = 2
IOP_MSG_SUBID_AUDIO_TX_START = 1
IOP_MSG_SUBID_AUDIO_TX_DATA = 2

IOP_MSG_ID_AUDIO_ABORT = 3
IOP_MSG_SUBID_AUDIO_CANCEL = 1
IOP_MSG_SUBID_AUDIO_ABORTED = 2

IOP_MSG_ID_CLASSIFIER_INFO = 7
IOP_MSG_SUBID_RQST_INFO = 1
IOP_MSG_SUBID_INFO = 2

IOP_MSG_ID_CMD = 8
IOP_MSG_SUBID_CMD_SET_MODEL_PATH = 1
IOP_MSG_SUBID_CMD_TERMINATE = 0xFF

IOP_MSG_ID_LOG_EVENT = 100
IOP_MSG_SUBID_LOG_INFO = 0

class IOP_audio_classifier:

    #----------------------------------------------
    # Local constants
    #----------------------------------------------
    iop_AUDIO_STATE_IDLE = 0
    iop_AUDIO_STATE_RX = 1
    iop_AUDIO_STATE_CLASSIFY = 2
    iop_AUDIO_STATE_TX = 3

    SAMPLE_SIZE_BYTES = 2
    PROBABILITY_SIZE_BYTES = 1

    TX_BAUD_RATE_PER_CYCLE = 8192

    #----------------------------------------------
    # Local variables
    #----------------------------------------------
    _state = iop_AUDIO_STATE_IDLE

    _rx_audio_buffer = []
    _rx_sequence_length = 0
    _rx_next_offset = 0
    _rx_sample_rate = 0

    _tx_probability_buffer = []
    _tx_offset = 0

    #----------------------------------------------
    # Procedures
    #----------------------------------------------
    def __init__( self ):
        self._reset_everything()

    def audio_input( self ):
        try:
            while True:
                bytes_read = IOP_get_bytes()
                frame = IOP_get_frame()

                if( frame is not None ):
                    id, subid, len, data = frame
                    #print( "Rxd message %u %u %u" % ( id, subid, len ), file= sys.stderr )
                    self._process_message( frame )
                else:
                    if( bytes_read == 0 ):
                        break

        except BaseException as e:
            #traceback.print_exc( file = sys.stderr )
            IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, traceback.format_exc().encode("ascii") )
            self._abort( str(e).encode("ascii") )
            SYS.SYS_main.SYS_main_stop_loop()

    def _process_message( self, message ):
        id, subid, len, data = message

        if( id == IOP_MSG_ID_CLASSIFIER_INFO ):
            if( subid == IOP_MSG_SUBID_RQST_INFO and len == struct.calcsize("<I") ):
                rqst_sample_rate, = struct.unpack( "<I", data )
                AI_load_classifier( rqst_sample_rate )
                status, sample_rate, inputs, outputs, offset = AI_get_properties()
                response_msg = struct.pack( '<IIHHH', status, sample_rate, inputs, outputs, offset )
                IOP_put_frame( IOP_MSG_ID_CLASSIFIER_INFO, IOP_MSG_SUBID_INFO, response_msg )
            return

        if( id == IOP_MSG_ID_AUDIO_RX ):
            if( subid == IOP_MSG_SUBID_AUDIO_RX_START ):
                if( self._state == self.iop_AUDIO_STATE_IDLE ):
                    self._reset_everything()
                    self._rx_sequence_length, self._rx_sample_rate = struct.unpack( "<II", data )
                    self._state = self.iop_AUDIO_STATE_RX
                else:
                    IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, "RXd AUDIO START RX in non IDLE state %u" % self._state )
                    self._abort( b'Classifier not ready for receiving audio data START' )

            elif( subid == IOP_MSG_SUBID_AUDIO_RX_DATA ):
                if( self._state == self.iop_AUDIO_STATE_RX ):
                    hdr_frmt = "<IH"
                    hdr_size = struct.calcsize( hdr_frmt )
                    offset, sample_cnt = struct.unpack( hdr_frmt, data[ :hdr_size ] )
                    if( ( offset != self._rx_next_offset )
                     or ( sample_cnt * self.SAMPLE_SIZE_BYTES + hdr_size ) != len
                     or ( offset + sample_cnt > self._rx_sequence_length ) ):
                        IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, "RXd AUDIO DATA RX with invalid offset %u, expected %u, or with invalid length" % ( offset, self._rx_next_offset ) )
                        self._abort( b'Invalid audio offset/length' )

                    else:
                        self._rx_next_offset += sample_cnt
                        self._rx_audio_buffer += struct.unpack( "<" + ( "h" * sample_cnt ), data[ hdr_size: ] )
                        if( self._rx_next_offset == self._rx_sequence_length ):
                            self._state = self.iop_AUDIO_STATE_CLASSIFY
                            self._classify_samples()
                else:
                    IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, "RXd AUDIO DATA RX in unexpected state %u" % self._state )
                    self._abort( b'Classifier not ready for receiving audio data buffers' )
            return

        if( id == IOP_MSG_ID_AUDIO_ABORT ):
            if( subid == IOP_MSG_SUBID_AUDIO_CANCEL ):
                self._reset_everything()
                #print( "Aborted by client", sys.stderr )
            return

        if( id == IOP_MSG_ID_CMD ):
            if( subid == IOP_MSG_SUBID_CMD_TERMINATE ):
                SYS.SYS_main.SYS_main_stop_loop()
                return

    def _reset_everything( self ):
        self._state = self.iop_AUDIO_STATE_IDLE
        self._rx_audio_buffer = []
        self._rx_sequence_length = 0
        self._rx_next_offset = 0
        self._tx_probability_buffer = []
        self._tx_offset = 0

    def _abort( self, reason ):
        self._reset_everything()
        IOP_put_frame( IOP_MSG_ID_AUDIO_ABORT, IOP_MSG_SUBID_AUDIO_ABORTED, reason )

    def _classify_samples( self ):
        self._tx_probability_buffer = []
        self._tx_offset = 0

        status, sample_rate, inputs, outputs, _ = AI_get_properties()
        if( status != IOP_AI_STATUS_OK ):
            self._abort( b'Model not loaded!' )
            return

        if( sample_rate != self._rx_sample_rate ):
            self._abort( b'Loaded model was trained for a different sample rate than the received audio\'s sample rate' )
            return

        outputs_cnt = self._rx_sequence_length - inputs + outputs
        if( outputs_cnt <= 0 or self._rx_sequence_length < inputs ):
            self._abort( b'Not enough input data to run the classifier' )
            return

        status, probabilities = AI_audio_classify( self._rx_audio_buffer )
        if( status == IOP_AI_STATUS_OK ):
            self._tx_probability_buffer = probabilities
        else:
            self._abort( b'Running the classifier returned a failed status' )
            return

    def audio_periodic_output( self ):
        tick_now = SYS_get_time_ms()
        txd_bytes = 0

        if( self._state == self.iop_AUDIO_STATE_CLASSIFY ):
            start_msg = struct.pack( "<I", len( self._tx_probability_buffer ) )
            txd_bytes += IOP_put_frame( IOP_MSG_ID_AUDIO_TX, IOP_MSG_SUBID_AUDIO_TX_START, start_msg )

            self._state = self.iop_AUDIO_STATE_TX
            self._tx_offset = 0

        if( self._state == self.iop_AUDIO_STATE_TX ):
            while( self._tx_offset < len( self._tx_probability_buffer ) ):
                chunk_length = len( self._tx_probability_buffer ) - self._tx_offset
                max_tx_bytes = min( IOP_MSG_MAX_DATA_LENGTH, self.TX_BAUD_RATE_PER_CYCLE - txd_bytes )
                if( chunk_length * self.PROBABILITY_SIZE_BYTES > max_tx_bytes ):
                    chunk_length = max_tx_bytes // self.PROBABILITY_SIZE_BYTES

                # If there's no data to be sent, exit the loop
                if( chunk_length <= 0 ):
                    break

                data_msg = struct.pack( "<IH", self._tx_offset, chunk_length )
                data_msg += struct.pack( "B" * chunk_length ,
                                         *[ int( i * 255 ) for i in self._tx_probability_buffer[ self._tx_offset:self._tx_offset + chunk_length ] ] )
                txd_bytes += IOP_put_frame( IOP_MSG_ID_AUDIO_TX, IOP_MSG_SUBID_AUDIO_TX_DATA, data_msg )

                self._tx_offset += chunk_length

                # If we sent all our cycle bandwidth, exit the loop
                if( txd_bytes >= self.TX_BAUD_RATE_PER_CYCLE ):
                    break

            if( self._tx_offset >= len( self._tx_probability_buffer ) ):
                self._reset_everything()
