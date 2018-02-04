package SignalProcessing.Effects;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.IAudioDataSource;
import ProjectStatics.ProjectStatics;
import Utils.Interval;

import java.util.Arrays;

/**
 * Created by Alex on 16.01.2018.
 */
public class Repair_Marked implements IEffect
{
    private int min_fetch_size = 512;
    private float min_fetch_ratio = 32;
    @Override
    public String getName()
    {
        return "Repair marked";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        int start = Math.max( 0, interval.l );
        int end = Math.min( dataSource.get_sample_number(), interval.r );
        Repair effect = new Repair();
        Interval i;
        int second;

        for( int k = 0; k < dataSource.get_channel_number(); k++ )
        {
            second = 0;
            effect.setAffected_channels( Arrays.asList( k ) );
            i = ProjectStatics.getMarkerFile().getNextMark( 0, k );
            while( i != null )
            {
                if( i.l >= start && i.r < end )
                {
                    effect.set_fetch_ratio( Math.max( min_fetch_size / ( interval.get_length() ), min_fetch_ratio ) );
                    try
                    {
                        effect.apply( dataSource, dataDest, i );
                    }
                    catch( DataSourceException ex )
                    {
                        ex.printStackTrace();
                    }
                    if( i.l / dataSource.get_sample_rate() > second )
                    {
                        second = i.l / dataSource.get_sample_rate();
                        System.out.println( "Processing at second " + second + " on channel " + k );
                    }
                }
                i = ProjectStatics.getMarkerFile().getNextMark( i.r, k );
            }
        }
    }

    public void setMin_fetch_ratio( float min_fetch_ratio )
    {
        this.min_fetch_ratio = min_fetch_ratio;
    }

    public void setMin_fetch_size( int min_fetch_size )
    {
        this.min_fetch_size = min_fetch_size;
    }
}
