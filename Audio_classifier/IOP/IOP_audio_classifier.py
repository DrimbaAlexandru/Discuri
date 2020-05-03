import traceback

from IOP.IOP_stdio import *
from SYS.SYS_time import *
import SYS.SYS_main
from AI.AI_audio_classifier import *

class IOP_audio_classifier:

    #----------------------------------------------
    # Local constants
    #----------------------------------------------
    STATUS_TX_PERIOD = 5000

    iop_AUDIO_STATE_IDLE = 0
    iop_AUDIO_STATE_RX = 1
    iop_AUDIO_STATE_CLASSIFY = 2
    iop_AUDIO_STATE_TX = 3

    SAMPLE_SIZE_BYTES = 2
    PROBABILITY_SIZE_BYTES = 1

    MAX_AUDIO_TX_MSGS_PER_CYCLE = 2

    #----------------------------------------------
    # Local variables
    #----------------------------------------------
    _state = iop_AUDIO_STATE_IDLE

    _rx_audio_buffer = []
    _rx_sequence_length = 0
    _rx_next_offset = 0

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
                IOP_get_bytes()
                frame = IOP_get_frame()

                if( frame is None ):
                    return
                else:
                    id, subid, len, data = frame
                    print( "Rxd message %u %u %u" % ( id, subid, len ) )
                    self._process_message( frame )

        except BaseException as e:
            traceback.print_exc()
            self._abort()
            IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, traceback.format_exc() )

    def _process_message( self, message ):
        id, subid, len, data = message

        if( id == IOP_MSG_ID_CLASSIFIER_INFO ):
            if( subid == IOP_MSG_SUBID_RQST_INFO ):
                response_msg = struct.pack( '<IHHH', AI_AUDIO_SAMPLE_RATE, AI_AUDIO_INPUTS, AI_AUDIO_OUTPUTS, AI_AUDIO_OFFSET )
                IOP_put_frame( IOP_MSG_ID_CLASSIFIER_INFO, IOP_MSG_SUBID_INFO, response_msg)
            return

        if( id == IOP_MSG_ID_AUDIO_RX ):
            if( subid == IOP_MSG_SUBID_AUDIO_RX_START ):
                if( self._state == self.iop_AUDIO_STATE_IDLE ):
                    self._reset_everything()
                    self._rx_sequence_length, = struct.unpack( "<I", data )
                    self._state = self.iop_AUDIO_STATE_RX
                else:
                    print( "RXd AUDIO START RX in non IDLE state %u" % self._state )
                    self._abort()
                    IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, b'Classifier not ready for receiving audio data START' )

            elif( subid == IOP_MSG_SUBID_AUDIO_RX_DATA ):
                if( self._state == self.iop_AUDIO_STATE_RX ):
                    hdr_frmt = "<IH"
                    hdr_size = struct.calcsize( hdr_frmt )
                    offset, sample_cnt = struct.unpack( hdr_frmt, data[ :hdr_size ] )
                    if( ( offset != self._rx_next_offset )
                     or ( sample_cnt * self.SAMPLE_SIZE_BYTES + hdr_size ) != len
                     or ( offset + sample_cnt > self._rx_sequence_length ) ):
                        print( "RXd AUDIO DATA RX with invalid offset %u, expected %u, or with invalid length" % ( offset, self._rx_next_offset ) )
                        self._abort()
                        IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, b'Invalid audio offset/length' )
                    else:
                        self._rx_next_offset += sample_cnt
                        self._rx_audio_buffer += struct.unpack( "<" + ( "h" * sample_cnt ), data[ hdr_size: ] )
                        if( self._rx_next_offset == self._rx_sequence_length ):
                            self._state = self.iop_AUDIO_STATE_CLASSIFY
                            self._classify_samples()
                else:
                    print( "RXd AUDIO DATA RX in unexpected state %u" % self._state )
                    self._abort()
                    IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, b'Classifier not ready for receiving audio data buffers' )
            return

        if( id == IOP_MSG_ID_AUDIO_ABORT ):
            if( subid == IOP_MSG_SUBID_AUDIO_CANCEL ):
                self._abort()
            return

        if( id == IOP_MSG_ID_CMD ):
            if( subid == IOP_MSG_SUBID_CMD_TERMINATE ):
                SYS.SYS_main.SYS_run_loop = False
                return

    def _reset_everything( self ):
        self._state = self.iop_AUDIO_STATE_IDLE
        self._rx_audio_buffer = []
        self._rx_sequence_length = 0
        self._rx_next_offset = 0
        self._tx_probability_buffer = []
        self._tx_offset = 0

    def _abort( self ):
        self._reset_everything()
        IOP_put_frame( IOP_MSG_ID_AUDIO_ABORT, IOP_MSG_SUBID_AUDIO_ABORTED, None )

    def _classify_samples( self ):
        self._tx_probability_buffer = []
        self._tx_offset = 0

        outputs_cnt = self._rx_sequence_length - AI_AUDIO_INPUTS + AI_AUDIO_OUTPUTS
        if( outputs_cnt <= 0 or self._rx_sequence_length < AI_AUDIO_INPUTS ):
            IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, b'Not enough input data to run the classifier' )
            self._abort()
            return

        self._tx_probability_buffer = AI_audio_classify( self._rx_audio_buffer )

    def audio_periodic_output( self ):
        tick_now = SYS_get_time_ms()

        if( self._state == self.iop_AUDIO_STATE_CLASSIFY ):
            start_msg = struct.pack( "<I", len( self._tx_probability_buffer ) )
            IOP_put_frame( IOP_MSG_ID_AUDIO_TX, IOP_MSG_SUBID_AUDIO_TX_START, start_msg )

            self._state = self.iop_AUDIO_STATE_TX
            self._tx_offset = 0

        if( self._state == self.iop_AUDIO_STATE_TX ):
            msg_nr = 0
            while( ( msg_nr < self.MAX_AUDIO_TX_MSGS_PER_CYCLE )
               and ( self._tx_offset < len( self._tx_probability_buffer ) ) ):
                chunk_length = len( self._tx_probability_buffer ) - self._tx_offset
                if( chunk_length * self.PROBABILITY_SIZE_BYTES > IOP_MSG_MAX_DATA_LENGTH ):
                    chunk_length = IOP_MSG_MAX_DATA_LENGTH / self.PROBABILITY_SIZE_BYTES

                data_msg = struct.pack( "<IH", self._tx_offset, chunk_length )
                data_msg += struct.pack( "B" * chunk_length ,
                                         *[ i * 255 for i in self._tx_probability_buffer[ self._tx_offset:self._tx_offset + chunk_length ] ] )
                IOP_put_frame( IOP_MSG_ID_AUDIO_TX, IOP_MSG_SUBID_AUDIO_TX_DATA, data_msg )

                self._tx_offset += chunk_length

            if( self._tx_offset >= len( self._tx_probability_buffer ) ):
                self._reset_everything()
