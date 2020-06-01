package SignalProcessing.SampleClassifier;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import MarkerFile.MarkerFile;
import ProjectManager.ProjectManager;
import SignalProcessing.Effects.IEffect;
import SignalProcessing.SampleClassifier.RemoteAIServer.IOP_IPC_stdio;
import SignalProcessing.SampleClassifier.RemoteAIServer.IOP_msg_struct_type;
import Utils.Interval;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class AIDamageRecognition implements IEffect
{
    /******************************************
    * Local types
    ********************************************/
    private enum effectState
    {
        iop_AUDIO_STATE_IDLE,
        iop_AUDIO_STATE_TX_AUDIO_START,
        iop_AUDIO_STATE_TX_AUDIO,
        iop_AUDIO_STATE_RX_PROBABILITY_START,
        iop_AUDIO_STATE_RX_PROBABILITY
    }

    private class classifierInfo
    {
        int inputs;
        int outputs;
        int sample_rate;
        int offset;
    }

    /******************************************
    * Local constants
    *
    * Message IDs and Sub-IDs
    ********************************************/
    public static final short IOP_MSG_ID_AUDIO_RX = 1;
    public static final short IOP_MSG_SUBID_AUDIO_RX_START = 1;
    public static final short IOP_MSG_SUBID_AUDIO_RX_DATA = 2;

    public static final short IOP_MSG_ID_AUDIO_TX = 2;
    public static final short IOP_MSG_SUBID_AUDIO_TX_START = 1;
    public static final short IOP_MSG_SUBID_AUDIO_TX_DATA = 2;

    public static final short IOP_MSG_ID_AUDIO_ABORT = 3;
    public static final short IOP_MSG_SUBID_AUDIO_CANCEL = 1;
    public static final short IOP_MSG_SUBID_AUDIO_ABORTED = 2;

    public static final short IOP_MSG_ID_CLASSIFIER_INFO = 7;
    public static final short IOP_MSG_SUBID_RQST_INFO = 1;
    public static final short IOP_MSG_SUBID_INFO = 2;

    public static final short IOP_MSG_ID_CMD = 8;
    public static final short IOP_MSG_SUBID_CMD_TERMINATE = 0xFF;

    public static final short IOP_MSG_ID_LOG_EVENT = 100;

    public static final short IOP_AI_STATUS_OK = 0;
    public static final short IOP_AI_STATUS_FAIL = 1;
    public static final short IOP_AI_STATUS_MODEL_NOT_EXISTING = 2;

    private static final int AUDIO_MS_PER_ROUND = 1000;         /* Amount of audio data (in seconds) to classify in each round                              */
    private static final int CLASSIFICATION_TIMEOUT_RATE = 10;  /* If it takes more than 10 seconds to process one second of data, consider it a timeout    */
    private static final long SCRIPT_LOAD_TIMEOUT_MS = 30*1000; /* Maximum amount of time to await for the script to load                                   */
    private static final int SAMPLE_SIZE = 2;                   /* Bytes for each data sample                                                               */
    private static final int PERIOD_RATE_MS = 1;                /* Periodic processing rate                                                                 */
    private static final int TX_BAUD_RATE_PER_CYCLE = 8192;     /* Max TX bytes per processing cycle                                                        */
    // private static final int MAX_ATTEMPT_NBMR = 2;              /* Maximum number of attempts/retries                                                       */

    /******************************************
    * Local variables
    ********************************************/
    private IOP_IPC_stdio ipc_io;
    private long last_rx = 0;
    private long last_tx = 0;
    private int last_txd_offset = 0;                            /* Last sent audio offset, relative to the beginning of the current round */
    private int last_rxd_offset = 0;                            /* Offset of last received probability, relative to the beginning of the current round */
    private volatile float progress = 0.0f;
    private classifierInfo classifierInfo = null;
    private effectState state;
    private volatile boolean aborted = false;
    private boolean aborted_by_remote = false;

    private ByteBuffer data_buffer = ByteBuffer.allocate( IOP_IPC_stdio.IOP_MSG_MAX_DATA_LENGTH ).order( ByteOrder.LITTLE_ENDIAN );
    private int round_index = -1;
    private int round_channel = -1;
    private int round_size = -1;

    private float threshold = 0.5f;

    /******************************************
    * Procedures
    ********************************************/
    public AIDamageRecognition() throws DataSourceException
    {
        try
        {
            ipc_io = ProjectManager.get_classifier_ipc();
            if( ipc_io == null )
            {
                ProjectManager.start_classifier_process();
                ipc_io = ProjectManager.get_classifier_ipc();
            }
        }
        catch( IOException | InterruptedException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.REMOTE_ERROR );
        }
    }


    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        int channel;
        int window_start_idx;
        int window_size;
        long rqst_time = 0;
        long crnt_time = System.currentTimeMillis();
        Interval orig_interval = new Interval( interval.l, interval.get_length() );

        progress = 0;
        state = effectState.iop_AUDIO_STATE_IDLE;
        aborted = false;
        aborted_by_remote = false;

        try
        {
            if( classifierInfo == null || classifierInfo.sample_rate != dataSource.get_sample_rate() )
            {
                rqst_time = System.currentTimeMillis();
                sendClassifierInfoRequest( dataSource.get_sample_rate() );
                while( ( classifierInfo == null ) && ( crnt_time < rqst_time + SCRIPT_LOAD_TIMEOUT_MS ) )
                {
                    Thread.sleep( PERIOD_RATE_MS );
                    processInput();
                    crnt_time = System.currentTimeMillis();
                }
            }
        }
        catch( IOException|InterruptedException e )
        {
            try
            {
                ProjectManager.stop_classifier_process( false );
            }
            catch( IOException | InterruptedException e2 )
            {
                e2.printStackTrace();
            }
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }

        if( classifierInfo == null || classifierInfo.sample_rate != dataSource.get_sample_rate() )
        {
            try
            {
                ProjectManager.stop_classifier_process( true );
            }
            catch( Exception ignored )
            {
                ;
            }

            throw new DataSourceException( "Failed getting a valid Sample Classifier info", DataSourceExceptionCause.REMOTE_TIMEOUT );
        }

        interval.l -= classifierInfo.offset;
        interval.r += classifierInfo.inputs - classifierInfo.outputs - classifierInfo.offset;
        /* Make sure the sent interval contains sufficient samples for the classifier's inputs */
        interval.r = Math.max( interval.r, interval.l + classifierInfo.inputs );
        interval.limit( 0, dataSource.get_sample_number() );

        try
        {
            for( window_start_idx = interval.l; window_start_idx < interval.r - ( classifierInfo.inputs - classifierInfo.outputs ); )
            {
                window_size = interval.r - window_start_idx;
                if( window_size > AUDIO_MS_PER_ROUND * dataSource.get_sample_rate() / 1000 )
                {
                    window_size = AUDIO_MS_PER_ROUND * dataSource.get_sample_rate() / 1000;
                }

                AudioSamplesWindow asw = dataSource.get_samples( window_start_idx, window_size );

                for( channel = 0; channel < asw.get_channel_number(); channel++ )
                {
                    state = effectState.iop_AUDIO_STATE_TX_AUDIO_START;
                    last_txd_offset = 0;
                    round_index = window_start_idx;
                    round_channel = channel;
                    round_size = window_size;

                    progress = ( window_start_idx - interval.l + asw.get_length() * 1.0f * channel / asw.get_channel_number() ) * 1.0f / interval.get_length();

                    do
                    {
                        //crnt_time = System.currentTimeMillis();

                        if( aborted )
                        {
                            throw new DataSourceException( "Aborted by used", DataSourceExceptionCause.ABORTED_BY_USER );
                        }

                        processInput();

                        processPeriodicOutput( asw, channel, 0, asw.get_length(), dataSource.get_sample_rate() );

                        Thread.sleep( PERIOD_RATE_MS );                        crnt_time = System.currentTimeMillis();
                    }
                    while( ( state != effectState.iop_AUDIO_STATE_IDLE ) && ( crnt_time - last_tx <  CLASSIFICATION_TIMEOUT_RATE * AUDIO_MS_PER_ROUND ) );

                    /* If the full response hasn't been received within the timeout period */
                    if( state != effectState.iop_AUDIO_STATE_IDLE )
                    {
                        try
                        {
                            ProjectManager.stop_classifier_process( true );
                        }
                        catch( Exception ignored )
                        {
                            ;
                        }
                        throw new DataSourceException( "Remote process classification took more than 10s for a 1s audio batch. Assuming stalled application", DataSourceExceptionCause.REMOTE_TIMEOUT );
                    }
                }
                window_start_idx += window_size - ( classifierInfo.inputs - classifierInfo.outputs );
            }
        }
        catch( IOException|InterruptedException|DataSourceException e )
        {
            if( aborted && !aborted_by_remote )
            {
                try
                {
                    ProjectManager.stop_classifier_process( false );
                }
                catch( IOException | InterruptedException e2 )
                {
                    e2.printStackTrace();
                }
            }
            if( e instanceof DataSourceException )
            {
                throw ( DataSourceException )e;
            }
            DataSourceException ex = new DataSourceException( e.getMessage(), DataSourceExceptionCause.REMOTE_ERROR );
            ex.setStackTrace( e.getStackTrace() );
            throw ex;
        }
        progress = 1.0f;
    }


    @Override
    public float getProgress()
    {
        return progress;
    }


    private void processInput() throws IOException, DataSourceException
    {
        IOP_msg_struct_type msg;
        int bytes_read = 0;

        while( true )
        {
            bytes_read = ipc_io.IOP_get_bytes();
            msg = ipc_io.IOP_get_frame();

            if( msg != null )
            {
                switch( msg.ID )
                {
                    case IOP_MSG_ID_AUDIO_TX:
                        processAudioTXMessage( msg );
                        break;

                    case IOP_MSG_ID_AUDIO_ABORT:
                        if( msg.subID == IOP_MSG_SUBID_AUDIO_ABORTED )
                        {
                            aborted = true;
                            aborted_by_remote = true;
                            throw new DataSourceException( new String( msg.data, StandardCharsets.US_ASCII ), DataSourceExceptionCause.REMOTE_ERROR );
                        }
                        break;

                    case IOP_MSG_ID_CLASSIFIER_INFO:
                        processClassifierInfoResponse( msg );
                        break;

                    case IOP_MSG_ID_LOG_EVENT:
                        System.out.println( new String( msg.data, StandardCharsets.US_ASCII ) );
                        break;

                    default:
                        break;
                }
            }
            else
            {
                if( bytes_read == 0 )
                    break;
            }
        }
    }


    private void processAudioTXMessage( IOP_msg_struct_type msg ) throws DataSourceException
    {
        if( msg.subID == IOP_MSG_SUBID_AUDIO_TX_START )
        {
            if( state == effectState.iop_AUDIO_STATE_RX_PROBABILITY_START )
            {
                int length = ByteBuffer.wrap( msg.data ).order( ByteOrder.LITTLE_ENDIAN ).getInt();

                if( length != round_size - ( classifierInfo.inputs - classifierInfo.outputs ) )
                {
                    throw new DataSourceException( "Number of returned classified samples does not match expectation", DataSourceExceptionCause.REMOTE_ERROR );
                }
                state = effectState.iop_AUDIO_STATE_RX_PROBABILITY;
                last_rxd_offset = 0;
            }
        }
        else if( msg.subID == IOP_MSG_SUBID_AUDIO_TX_DATA )
        {
            if( state == effectState.iop_AUDIO_STATE_RX_PROBABILITY )
            {
                int offset, count, i;
                short probability;
                ByteBuffer bb = ByteBuffer.wrap( msg.data ).order( ByteOrder.LITTLE_ENDIAN );
                MarkerFile mf = ProjectManager.getMarkerFile();
                Interval mark = null;

                offset = bb.getInt();
                count = bb.getShort();

                if( offset != last_rxd_offset )
                {
                    throw new DataSourceException( "Received offset does not match expected offset", DataSourceExceptionCause.REMOTE_ERROR );
                }
                for( i = 0; i < count; i++ )
                {
                    probability = ( short )( bb.get() & 0xFF ); /* cast to short, to allow range of unsigned byte */
                    if( probability >= threshold * 255 )
                    {
                        if( mark == null )
                        {
                            mark = new Interval( round_index + last_rxd_offset + i + classifierInfo.offset, 1 );
                        }
                        else
                        {
                            mark.r = round_index + last_rxd_offset + i + classifierInfo.offset + 1;
                        }
                    }
                    else
                    {
                        if( mark != null )
                        {
                            if( mark.get_length() > 0 )
                            {
                                mf.addMark( mark.l, mark.r - 1, round_channel );
                            }
                            mark = null;
                        }
                    }
                }

                if( mark != null )
                {
                    if( mark.get_length() > 0 )
                    {
                        mf.addMark( mark.l, mark.r - 1, round_channel );
                    }
                }

                last_rxd_offset += count;
                if( last_rxd_offset >= ( round_size - ( classifierInfo.inputs - classifierInfo.outputs ) ) )
                {
                    state = effectState.iop_AUDIO_STATE_IDLE;
                }
            }
        }
    }


    private void processClassifierInfoResponse( IOP_msg_struct_type msg ) throws DataSourceException
    {
        classifierInfo = null;

        ByteBuffer bb = ByteBuffer.wrap( msg.data ).order( ByteOrder.LITTLE_ENDIAN );
        if( msg.subID == IOP_MSG_SUBID_INFO && msg.size == 14 )
        {
            int status = bb.getInt();
            if( status == IOP_AI_STATUS_OK )
            {
                classifierInfo = new classifierInfo();

                classifierInfo.sample_rate = bb.getInt();
                classifierInfo.inputs = bb.getShort();
                classifierInfo.outputs = bb.getShort();
                classifierInfo.offset = bb.getShort();
            }
            else
            {
                throw new DataSourceException( String.format( "Classifier Info response returned failed status %d", status ), DataSourceExceptionCause.REMOTE_ERROR );
            }
        }

    }


    private void processPeriodicOutput( AudioSamplesWindow asw, int channel, int window_idx, int seq_length, int sample_rate ) throws IOException
    {
        int txd_bytes = 0;
        int chunk_length;
        int max_tx_bytes;

        switch( state )
        {
            case iop_AUDIO_STATE_TX_AUDIO_START:
                txd_bytes += sendAudioDataStartRX( sample_rate, seq_length );
                state = effectState.iop_AUDIO_STATE_TX_AUDIO;
                /* intentional fallthrough */

            case iop_AUDIO_STATE_TX_AUDIO:
                while( last_txd_offset < seq_length )
                {
                    chunk_length = seq_length - last_txd_offset;
                    max_tx_bytes = Math.min( IOP_IPC_stdio.IOP_MSG_MAX_DATA_LENGTH, TX_BAUD_RATE_PER_CYCLE - txd_bytes );
                    if( chunk_length * SAMPLE_SIZE > max_tx_bytes )
                    {
                        chunk_length = max_tx_bytes / SAMPLE_SIZE;
                    }

                    /* If there's no data to be sent, exit the loop */
                    if( chunk_length <= 0 )
                    {
                        break;
                    }

                    txd_bytes += sendAudioData( asw, channel, window_idx + last_txd_offset, chunk_length );
                    last_txd_offset += chunk_length;
                    last_tx = System.currentTimeMillis();

                    /* If we sent all our cycle bandwidth, exit the loop */
                    if( txd_bytes >= TX_BAUD_RATE_PER_CYCLE )
                    {
                        break;
                    }
                }
                if( last_txd_offset == seq_length )
                {
                    state = effectState.iop_AUDIO_STATE_RX_PROBABILITY_START;
                }
                break;

            default:
                break;
        }
    }


    private void sendClassifierInfoRequest( int sample_rate ) throws IOException
    {
        IOP_msg_struct_type msg = new IOP_msg_struct_type();

        msg.ID = IOP_MSG_ID_CLASSIFIER_INFO;
        msg.subID = IOP_MSG_SUBID_RQST_INFO;

        data_buffer.rewind();
        data_buffer.putInt( sample_rate );

        msg.size = data_buffer.position();
        msg.data = data_buffer.array();

        ipc_io.IOP_put_frame( msg );
    }


    private int sendAudioDataStartRX( int sample_rate, int seq_length ) throws IOException
    {
        IOP_msg_struct_type msg = new IOP_msg_struct_type();

        /* Sent the AUDIO RX START message */
        msg.ID = IOP_MSG_ID_AUDIO_RX;
        msg.subID = IOP_MSG_SUBID_AUDIO_RX_START;

        data_buffer.rewind();
        data_buffer.putInt( seq_length );
        data_buffer.putInt( sample_rate );
        msg.size = data_buffer.position();
        msg.data = data_buffer.array();

        return ipc_io.IOP_put_frame( msg );
    }


    private int sendAudioData( AudioSamplesWindow asw, int channel, int seq_offset, int seq_length ) throws IOException
    {
        int i;
        int j;
        float samples[] = asw.getSamples()[ channel ];
        IOP_msg_struct_type msg = new IOP_msg_struct_type();

        data_buffer.rewind();
        msg.ID = IOP_MSG_ID_AUDIO_RX;
        msg.subID = IOP_MSG_SUBID_AUDIO_RX_DATA;

        data_buffer.putInt( seq_offset );
        data_buffer.putShort( ( short )seq_length );

        for( j = 0; j < seq_length; j++ )
        {
            if( samples[ seq_offset + j ] >= 1.0f )
            {
                data_buffer.putShort( Short.MAX_VALUE );
            }
            else if( samples[ seq_offset + j ] <= -1.0f )
            {
                data_buffer.putShort( Short.MIN_VALUE );
            }
            else
            {
                data_buffer.putShort( ( short )( samples[ seq_offset + j ] * ( Short.MAX_VALUE + 1 ) ) );
            }
        }

        msg.data = data_buffer.array();
        msg.size = data_buffer.position();
        return ipc_io.IOP_put_frame( msg );

    }

    public void setThreshold( float threshold )
    {
        this.threshold = threshold;
    }

}
