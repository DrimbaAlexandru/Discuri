package SignalProcessing.Effects;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.IAudioDataSource;
import MarkerFile.Marking;
import ProjectStatics.ProjectStatics;
import Utils.Interval;

import java.util.Arrays;
import java.util.List;

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
        Interval i = new Interval( 0, 0 );
        List< Marking > markings = ProjectStatics.getMarkerFile().get_all_markings( interval );
        int second = 0;

        for( Marking m : markings )
        {
            effect.setAffected_channels( Arrays.asList( m.getChannel() ) );
            i.l = m.get_first_marked_sample();
            i.r = m.get_last_marked_sample() + 1;
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
                    System.out.println( "Repairing at second " + second );
                }
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
