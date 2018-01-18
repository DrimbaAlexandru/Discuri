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
    @Override
    public String getName()
    {
        return "Repair marked";
    }

    @Override
    public void apply( IAudioDataSource dataSource, Interval interval ) throws DataSourceException
    {
        int start = Math.max( 0, interval.l );
        int end = Math.min( dataSource.get_sample_number(), interval.r );
        Repair effect = new Repair();
        effect.set_fetch_ratio( 16 );
        Interval i;

        for( int k = 0; k < dataSource.get_channel_number(); k++ )
        {
            effect.setAffected_channels( Arrays.asList( k ) );
            i = ProjectStatics.getMarkerFile().getNextMark( 0, k );
            while( i != null )
            {
                if( i.l >= start && i.r < end )
                {
                    effect.apply( dataSource, i );
                }
                i = ProjectStatics.getMarkerFile().getNextMark( i.r, k );
            }
        }
    }
}
