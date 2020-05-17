package SignalProcessing.SampleClassifier;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import Utils.Interval;
import Utils.MyPair;

public class Create_Probability_Graph
{
    public void create_graph( IAudioDataSource dataSource, IAudioDataSource destination ) throws DataSourceException
    {
        int i;
        int temp_len;
        int chunk_size = 44100;
        final int nn_input_size = 129;
        AudioSamplesWindow win;
        AudioSamplesWindow write_win;
        float[][] write_samples = new float[ dataSource.get_channel_number() ][];
        MyPair< Integer, float[] > prediction;
        Interval applying_interval = new Interval( 0, dataSource.get_sample_number() );

        applying_interval.limit( nn_input_size / 2, dataSource.get_sample_number() - nn_input_size / 2 );

        for( i = applying_interval.l; i < applying_interval.r; )
        {
            temp_len = Math.min( applying_interval.r - i + nn_input_size - 1, chunk_size );
            win = dataSource.get_samples( i - nn_input_size / 2, temp_len );
            System.out.println( "At sample " + i );
            write_win = new AudioSamplesWindow( write_samples, i, win.get_length() - nn_input_size + 1, dataSource.get_channel_number() );
            for( int ch = 0; ch < win.get_channel_number(); ch++ )
            {
                Classify_In_Python.send_float_array( win.get_length(), win.getSamples()[ ch ] );
                prediction = Classify_In_Python.get_float_array();
                if( prediction.getLeft() != win.get_length() - nn_input_size + 1 )
                {
                    throw new DataSourceException( "Received unexpected array length", DataSourceExceptionCause.REMOTE_ERROR );
                }
                float probabilities[] = prediction.getRight();
                write_samples[ ch ] = probabilities;
            }
            destination.put_samples( write_win );
            i += win.get_length() - nn_input_size + 1;
        }
    }
}
