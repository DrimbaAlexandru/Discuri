package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import MarkerFile.MarkerFile;
import ProjectManager.*;
import Utils.Interval;
import Utils.MyPair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Created by Alex on 07.04.2018.
 */
class Classify_In_Python
{
    private static final String script_exec = "python -u " +
                                              "\"" + ProjectStatics.getPython_classifier_script_path() + "\" " +
                                              "\"" + ProjectStatics.getPython_classifier_mlp_path() + "\" " +
                                              "\"" + ProjectStatics.getPython_classifier_scaler_path() + "\" ";

    private static Process proc = null;
    private static final int double_size = 8;
    private static final int int_size = 4;
    private static final int buffer_size = 1024 * 4 * double_size;
    private static final ByteBuffer buffer = ByteBuffer.allocate( buffer_size );

    private static void readBytes( int size, InputStream inp ) throws IOException
    {
        int read;
        int total_read = 0;
        while( total_read < size )
        {
            read = inp.read( buffer.array(), total_read, size - total_read );
            total_read += read;
            if( read < 0 )
            {
                throw new IOException( "Read returned negative value" );
            }
        }
    }

    private static Process getProcess() throws DataSourceException
    {
        if( proc == null )
        {
            try
            {
                System.out.println( "Starting python script" );
                proc = Runtime.getRuntime().exec( script_exec );
                InputStream err = proc.getErrorStream();
                buffer.clear();
                int len;

                readBytes( int_size, err );

                buffer.rewind();
                len = buffer.getInt();

                if( len > buffer.capacity() )
                {
                    proc.destroy();
                    proc = null;
                    throw new DataSourceException( "Python error message larger than buffer", DataSourceExceptionCause.PYTHON_COMMUNICATION_ERROR );
                }
                System.out.println( "Python script started" );
                buffer.clear();
                readBytes( len, err );
                buffer.rewind();
                buffer.limit( len );
                String errs = StandardCharsets.US_ASCII.decode( buffer ).toString();
                if( !errs.equals( "ok" ) )
                {
                    proc.destroy();
                    proc = null;
                    throw new DataSourceException( errs, DataSourceExceptionCause.PYTHON_COMMUNICATION_ERROR );
                }
            }
            catch( IOException e )
            {
                if( proc != null )
                {
                    proc.destroy();
                    proc = null;
                }
                throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.PYTHON_COMMUNICATION_ERROR );
            }
        }
        return proc;
    }

    public static void send_double_array( int length, double[] array ) throws DataSourceException
    {
        OutputStream out = getProcess().getOutputStream();
        try
        {
            buffer.clear();
            buffer.putInt( length );
            out.write( buffer.array(), 0, int_size );
            out.flush();
            buffer.clear();

            for( int i = 0; i < length; i++ )
            {
                buffer.putDouble( array[ i ] );
                if( buffer.position() == buffer.capacity() || i == length - 1 )
                {
                    out.write( buffer.array(), 0, buffer.position() );
                    out.flush();
                    buffer.clear();
                }
            }
        }
        catch( IOException e )
        {
            proc.destroy();
            proc = null;
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.PYTHON_COMMUNICATION_ERROR );
        }
    }

    public static MyPair< Integer, double[] > get_double_array() throws DataSourceException
    {
        int length = 0;
        double[] array = null;
        InputStream inp = getProcess().getInputStream();

        try
        {
            buffer.clear();
            readBytes( int_size, inp );
            buffer.rewind();

            length = buffer.getInt();
            array = new double[ length ];

            for( int i = 0; i < length; i++ )
            {
                if( buffer.position() == buffer.capacity() || i == 0 )
                {
                    buffer.clear();
                    readBytes( Math.min( buffer_size / double_size * double_size, ( length - i ) * double_size ), inp );
                }
                array[ i ] = buffer.getDouble();
            }
        }
        catch( IOException e )
        {
            proc.destroy();
            proc = null;
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.PYTHON_COMMUNICATION_ERROR );
        }
        return new MyPair< Integer, double[] >( length, array );
    }
}


public class Create_Marker_File implements IEffect
{
    private double threshold = 0.5;
    private int side_extend = 0;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        Interval applying_interval = new Interval( interval.l, interval.get_length() );
        MarkerFile mf = ProjectManager.getMarkerFile();

        int i;
        int temp_len;
        int chunk_size = 44100;
        final int nn_input_size = 129;
        AudioSamplesWindow win;
        MyPair< Integer, double[] > prediction;

        applying_interval.limit( nn_input_size / 2, dataSource.get_sample_number() - nn_input_size / 2 );

        for( i = applying_interval.l; i < applying_interval.r; )
        {
            int predict_start;
            temp_len = Math.min( applying_interval.r - i + nn_input_size - 1, chunk_size );
            win = dataSource.get_samples( i - nn_input_size / 2, temp_len );
            predict_start = win.get_first_sample_index() + nn_input_size / 2;
            //System.out.println( "At sample " + i );
            for( int ch = 0; ch < win.get_channel_number(); ch++ )
            {
                Classify_In_Python.send_double_array( win.get_length(), win.getSamples()[ ch ] );
                prediction = Classify_In_Python.get_double_array();
                if( prediction.getLeft() != win.get_length() - nn_input_size + 1 )
                {
                    throw new DataSourceException( "Received unexpected array length", DataSourceExceptionCause.PYTHON_COMMUNICATION_ERROR );
                }
                Interval mark = null;
                double probabilities[] = prediction.getRight();
                for( int s = 0; s < prediction.getLeft(); s++ )
                {
                    if( probabilities[ s ] > threshold )
                    {
                        if( mark == null )
                        {
                            mark = new Interval( predict_start + s, 1 );
                        }
                        else
                        {
                            mark.r = predict_start + s + 1;
                        }
                    }
                    else
                    {
                        if( mark != null )
                        {
                            if( mark.get_length() > 3 )
                            {
                                mf.addMark( mark.l - side_extend, mark.r - 1 + side_extend, ch );
                            }
                            mark = null;
                        }
                    }
                }
            }
            i += win.get_length() - nn_input_size + 1;
        }
    }

    @Override
    public double getProgress()
    {
        return 0;
    }

    public void setThreshold( double threshold )
    {
        this.threshold = threshold;
    }
}
