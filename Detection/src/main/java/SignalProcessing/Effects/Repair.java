package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.LiniarPrediction.BurgLP;
import SignalProcessing.LiniarPrediction.LinearPrediction;
import Utils.Interval;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 11.01.2018.
 */
public class Repair implements IEffect
{
    private float fetch_size_ratio = 4;
    private List< Integer > affected_channels = new ArrayList<>();

    @Override
    public String getName()
    {
        return "Repair";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        int side_fetch_size = ( int )( interval.get_length() * fetch_size_ratio );
        AudioSamplesWindow left_win, right_win, center_win;
        LinearPrediction LP = new BurgLP();
        if( side_fetch_size > interval.l || side_fetch_size > dataSource.get_sample_number() - interval.r )
        {
            throw new DataSourceException( "Not enough data to extrapolate from", DataSourceExceptionCause.INVALID_STATE );
        }
        left_win = dataSource.get_samples( interval.l - side_fetch_size, side_fetch_size );
        right_win = dataSource.get_samples( interval.r, side_fetch_size );
        center_win = dataDest.get_samples( interval.l, interval.get_length() );
        for( int k : affected_channels )
        {
            LP.extrapolate( left_win.getSamples()[ k ], center_win.getSamples()[ k ], right_win.getSamples()[ k ], side_fetch_size, interval.get_length(), side_fetch_size );
        }
        dataDest.put_samples( center_win );
    }

    public void set_fetch_ratio( float ratio )
    {
        fetch_size_ratio = ratio;
    }

    public void setAffected_channels( List<Integer> chs )
    {
        affected_channels.clear();
        affected_channels.addAll( chs );
    }
}
