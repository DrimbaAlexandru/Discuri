package SignalProcessing.SampleClassifier;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import MarkerFile.MarkerFile;
import ProjectManager.ProjectManager;
import SignalProcessing.Effects.IEffect;
import Utils.Interval;
import Utils.MyPair;

public class Create_Marker_File_Clipping implements IEffect
{

    private float threshold = 0.9f;
    private int side_extend = 0;
    private float max_slope = 0.001f;
    private float progress = 0;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        Interval applying_interval = new Interval( interval.l, interval.get_length() );
        MarkerFile mf = ProjectManager.getMarkerFile();

        int i;
        int temp_len;
        int chunk_size = dataSource.get_sample_rate();
        AudioSamplesWindow win;
        MyPair< Integer, float[] > prediction;

        applying_interval.limit( 0, dataSource.get_sample_number() );
        progress = 0;

        for( i = applying_interval.l; i < applying_interval.r - 1; )
        {
            int predict_start;
            temp_len = Math.min( applying_interval.r - i, chunk_size );
            win = dataSource.get_samples( i, temp_len );
            predict_start = win.get_first_sample_index();
            //System.out.println( "At sample " + i );
            for( int ch = 0; ch < win.get_channel_number(); ch++ )
            {
                Interval mark = null;
                for( int s = 0; s < temp_len - 1; s++ )
                {
                    if( ( Math.abs( win.getSamples()[ ch ][ s ] - win.getSamples()[ ch ][ s + 1 ] ) <= max_slope )
                     && ( Math.abs( win.getSamples()[ ch ][ s ] ) > threshold ) )
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
            i += win.get_length() - 1;
            progress = 1.0f * ( i - applying_interval.l ) / applying_interval.get_length();
        }
    }

    @Override
    public float getProgress()
    {
        return progress;
    }

    public void setThreshold( float threshold )
    {
        this.threshold = threshold;
    }

    public void setMax_slope( float max_slope )
    {
        this.max_slope = max_slope;
    }
}


