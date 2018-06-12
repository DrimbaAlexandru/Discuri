package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.LinearPrediction.BurgMethod;
import SignalProcessing.LinearPrediction.LinearPrediction;
import SignalProcessing.Windowing.Windowing;
import Utils.Interval;

import java.util.ArrayList;
import java.util.Arrays;
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
        AudioSamplesWindow window, center_win;
        Interval required_interval = new Interval( interval.l - side_fetch_size, interval.r + side_fetch_size, false );
        window = dataSource.get_samples( required_interval.l, required_interval.get_length() );
        center_win = dataSource.get_samples( interval.l, interval.get_length() );

        if( !window.getInterval().includes( required_interval ) )
        {
            throw new DataSourceException( "Not enough data to extrapolate from", DataSourceExceptionCause.INVALID_STATE );
        }
        for( int k : affected_channels )
        {
            double[] flp = Arrays.copyOfRange( window.getSamples()[ k ], 0, side_fetch_size + interval.get_length() );
            double[] blp = Arrays.copyOfRange( window.getSamples()[ k ], side_fetch_size, side_fetch_size * 2 + interval.get_length() );
            BurgMethod flpc = new BurgMethod( flp, 0, side_fetch_size, side_fetch_size - 1 );
            BurgMethod blpc = new BurgMethod( blp, interval.get_length(), side_fetch_size, side_fetch_size - 1 );
            LinearPrediction lp1 = new LinearPrediction( flpc.get_coeffs(), flpc.get_nr_coeffs() );
            LinearPrediction lp2 = new LinearPrediction( blpc.get_coeffs(), blpc.get_nr_coeffs() );
            lp1.predict_forward( flp, side_fetch_size, side_fetch_size + interval.get_length() );
            lp2.predict_backward( blp, interval.get_length(), 0 );
            Windowing.apply( flp, side_fetch_size, interval.get_length(), Windowing.cos_sq_window );
            Windowing.apply( blp, 0, interval.get_length(), Windowing.inv_cos_sq_window );
            for( int i = 0; i < interval.get_length(); i++ )
            {
                center_win.getSamples()[ k ][ i ] = flp[ side_fetch_size + i ] + blp[ i ];
            }
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
