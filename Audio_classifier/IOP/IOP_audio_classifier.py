from IOP.IOP_stdio import *
from SYS.SYS_time import *
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
        self._last_rx_tick = 0
        self._last_status_tx_tick = 0

    def audio_input( self ):
        while True:
            frame = IOP_get_frame()

            if( frame is None ):
                return
            else:
                id, subid, len, data = frame
                print( "Rxd message %u %u %u" % ( id, subid, len ) )
                self._process_message( frame )

    def _process_message( self, message ):
        id, subid, len, data = message

        if( id == IOP_MSG_ID_CLASSIFIER_INFO ):
            if( subid == IOP_MSG_SUBID_RQST_INFO ):
                response_msg = struct.pack( '<IHHH', self.NN_SAMPLE_RATE, self._nn_inputs, self._nn_outputs, self._nn_offset )
                IOP_put_frame( IOP_MSG_ID_CLASSIFIER_INFO, IOP_MSG_SUBID_INFO, response_msg)
            return

        if( id == IOP_MSG_ID_AUDIO_RX ):
            if( subid == IOP_MSG_SUBID_AUDIO_RX_START ):
                if( self._state == self.iop_AUDIO_STATE_IDLE ):
                    self._rx_audio_buffer = []
                    self._rx_sequence_length, = struct.unpack("<I", data )
                    self._rx_next_offset = 0
                    self._state = self.iop_AUDIO_STATE_RX
                else:
                    print( "RXd AUDIO START RX in non IDLE state %u" % self._state )
                    IOP_put_frame( IOP_MSG_ID_AUDIO_RX, IOP_MSG_SUBID_AUDIO_RX_ABORT, None )
                    IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, b'Classifier not ready for receiving audio data START' )

            elif( subid == IOP_MSG_SUBID_AUDIO_RX_DATA ):
                if( self._state == self.iop_AUDIO_STATE_RX ):
                    offset, sample_cnt = struct.unpack( "<II", data )
                    if( ( offset != self._rx_next_offset )
                     or ( sample_cnt * self.SAMPLE_SIZE_BYTES + 4 ) != len
                     or ( offset + sample_cnt > self._rx_sequence_length ) ):
                        print( "RXd AUDIO DATA RX with invalid offset %u, expected %u, or with invalid length" % ( offset, self._rx_next_offset ) )
                        IOP_put_frame( IOP_MSG_ID_AUDIO_RX, IOP_MSG_SUBID_AUDIO_RX_ABORT, None )
                        IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, b'Invalid audio offset/length' )
                    else:
                        self._rx_next_offset += sample_cnt
                        self._rx_audio_buffer += struct.unpack( "<" + ( "h" * sample_cnt ), data[ 8: ] )
                        if( self._rx_next_offset == self._rx_sequence_length ):
                            self._state = self.iop_AUDIO_STATE_CLASSIFY
                            self._classify_samples()
                else:
                    print( "RXd AUDIO DATA RX in unexpected state %u" % self._state )
                    IOP_put_frame( IOP_MSG_ID_AUDIO_RX, IOP_MSG_SUBID_AUDIO_RX_ABORT, None )
                    IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, b'Classifier not ready for receiving audio data buffers' )

            elif( subid == IOP_MSG_SUBID_AUDIO_RX_CANCEL ):
                if( self._state == self.iop_AUDIO_STATE_RX ):
                    self._state = self.iop_AUDIO_STATE_IDLE

    def _classify_samples( self ):
        self._tx_probability_buffer = []
        self._tx_offset = 0

        outputs_cnt = self._rx_sequence_length - self._nn_inputs + self._nn_outputs
        if( outputs_cnt <= 0 ):
            IOP_put_frame( IOP_MSG_ID_LOG_EVENT, 0, b'Not enough input data to run the classifier' )
            IOP_put_frame( IOP_MSG_ID_AUDIO_TX, IOP_MSG_SUBID_AUDIO_TX_ABORT, None )
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
            while( self._tx_offset < len( self._tx_probability_buffer ) ):
                chunk_length = len( self._tx_probability_buffer ) - self._tx_offset
                if( chunk_length * self.PROBABILITY_SIZE_BYTES > IOP_MSG_MAX_DATA_LENGTH ):
                    chunk_length = IOP_MSG_MAX_DATA_LENGTH / self.PROBABILITY_SIZE_BYTES

                data_msg = struct.pack( "<II", self._tx_offset, chunk_length )
                data_msg += struct.pack( "B" * chunk_length ,
                                         [ i * 255 for i in self._tx_probability_buffer[ self._tx_offset:self._tx_offset + chunk_length ] ] )
                IOP_put_frame( IOP_MSG_ID_AUDIO_TX, IOP_MSG_SUBID_AUDIO_TX_DATA, data_msg )

                self._tx_offset += chunk_length

        if( tick_now - self._last_status_tx_tick >= self.STATUS_TX_PERIOD ):
            IOP_put_frame( IOP_MSG_ID_STATUS, 0, None )
