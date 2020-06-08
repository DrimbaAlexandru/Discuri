package Effects;

import AudioDataSource.AudioSamplesWindow;
import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.LinearPrediction.BurgMethod;
import SignalProcessing.LinearPrediction.LinearPrediction;
import SignalProcessing.Windowing.Windowing;
import Utils.DataTypes.Interval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Alex on 11.01.2018.
 */
public class Repair_One implements IEffect
{
    private float fetch_size_ratio = 4;
    private List< Integer > affected_channels = new ArrayList<>();
    private float progress = 0;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        progress = 0;

        int side_fetch_size = ( int )( interval.get_length() * fetch_size_ratio );
        AudioSamplesWindow window, center_win;
        Interval required_interval = new Interval( interval.l - side_fetch_size, interval.r + side_fetch_size, false );
        window = dataSource.get_samples( required_interval.l, required_interval.get_length() );
        center_win = dataSource.get_samples( interval.l, interval.get_length() );
        int step = 0;

        if( !window.getInterval().includes( required_interval ) )
        {
            throw new DataSourceException( "Not enough data to extrapolate from", DataSourceExceptionCause.INVALID_STATE );
        }
        for( int k : affected_channels )
        {
            float[] flp = Arrays.copyOfRange( window.getSamples()[ k ], 0, side_fetch_size + interval.get_length() );
            float[] blp = Arrays.copyOfRange( window.getSamples()[ k ], side_fetch_size, side_fetch_size * 2 + interval.get_length() );

            double[] dflp = new double[ side_fetch_size + interval.get_length() ];
            double[] dblp = new double[ side_fetch_size + interval.get_length() ];
            for( int i = 0; i < side_fetch_size + interval.get_length(); i++ )
            {
                dflp[ i ] = flp[ i ];
                dblp[ i ] = blp[ i ];
            }

//Utils.Util_Stuff.plot_in_matlab( window.getSamples()[ k ], required_interval.get_length(), 0, "o" );

            BurgMethod flpc = new BurgMethod( flp, 0, side_fetch_size, side_fetch_size - 1 );
            step++;
            progress = ( 1.0f * step / ( 4 * affected_channels.size() ) );

            BurgMethod blpc = new BurgMethod( blp, interval.get_length(), side_fetch_size, side_fetch_size - 1 );
            step++;
            progress = ( 1.0f * step / ( 4 * affected_channels.size() ) );

            LinearPrediction lp1 = new LinearPrediction( flpc.get_coeffs(), flpc.get_nr_coeffs() );
            LinearPrediction lp2 = new LinearPrediction( blpc.get_coeffs(), blpc.get_nr_coeffs() );

            lp1.predict_forward( dflp, side_fetch_size, side_fetch_size + interval.get_length() );
            step++;
            progress = ( 1.0f * step / ( 4 * affected_channels.size() ) );

            lp2.predict_backward( dblp, interval.get_length(), 0 );
            step++;
            progress = ( 1.0f * step / ( 4 * affected_channels.size() ) );

//Utils.Util_Stuff.plot_in_matlab( flp, side_fetch_size + interval.get_length(),0,"fp" );
//Utils.Util_Stuff.plot_in_matlab( blp, side_fetch_size + interval.get_length() ,side_fetch_size,"bp");

            for( int i = 0; i < side_fetch_size + interval.get_length(); i++ )
            {
                flp[ i ] = ( float )dflp[ i ];
                blp[ i ] = ( float )dblp[ i ];
            }

            Windowing.apply( flp, side_fetch_size, interval.get_length(), Windowing.cos_sq_window );
            Windowing.apply( blp, 0, interval.get_length(), Windowing.inv_cos_sq_window );

//Utils.Util_Stuff.plot_in_matlab( flp, side_fetch_size + interval.get_length(),0,"wfp" );
//Utils.Util_Stuff.plot_in_matlab( blp, side_fetch_size + interval.get_length(),side_fetch_size,"wbp" );

            for( int i = 0; i < interval.get_length(); i++ )
            {
                center_win.getSamples()[ k ][ i ] = flp[ side_fetch_size + i ] + blp[ i ];
            }
//Utils.Util_Stuff.plot_in_matlab( window.getSamples()[ k ], required_interval.get_length(),0,"r" );

        }
        dataDest.put_samples( center_win );
    }

    @Override
    public float getProgress()
    {
        return progress;
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
