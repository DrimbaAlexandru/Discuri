package SignalProcessing.LiniarPrediction;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import SignalProcessing.Windowing.Windowing;

import java.util.function.Function;

/**
 * Created by Alex on 10.11.2017.
 */
public class BurgLP implements LinearPrediction
{

    public void extrapolate( double[] left, double[] center, double[] right, int l_length, int c_length, int r_length ) throws DataSourceException
    {
        if( ( l_length <= 1 ) || ( r_length <= 1 ) || ( c_length > l_length ) || ( c_length > r_length ) )
        {
            throw new DataSourceException( "Tried to extrapolate " + c_length + " samples from " +
                                                         l_length + " samples to the left and " +
                                                         r_length + "samples to the right ", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        final Function< Double, Double > left_window_function = Windowing.one_window;
        final Function< Double, Double > right_window_function = Windowing.one_window;
        final Function< Double, Double > left_component_window_function = Windowing.fade_out_window;
        double LR_balance;
        int i;

        double left_extrapolated_samples[] = new double[ c_length ];
        double right_extrapolated_samples[] = new double[ c_length ];

        Windowing.apply( left, l_length, left_window_function );
        Windowing.apply( right, r_length, right_window_function );

        BurgMethodExtrapolation l_bme = new BurgMethodExtrapolation( left, l_length, l_length - 1 );
        BurgMethodExtrapolation r_bme = new BurgMethodExtrapolation( right, r_length, r_length - 1 );
        l_bme.predict_forward( c_length, left_extrapolated_samples );
        r_bme.predict_backward( c_length, right_extrapolated_samples );

        //LR_balance = l_bme.forward_L_P_error() / r_bme.backward_L_P_error();

        Windowing.apply( left_extrapolated_samples, c_length, x -> left_component_window_function.apply( x ) );
        Windowing.apply( right_extrapolated_samples, c_length, x -> ( 1 - left_component_window_function.apply( x ) ) );

        for( i = 0; i < c_length; i++ )
        {
            center[ i ] = left_extrapolated_samples[ i ] + right_extrapolated_samples[ i ];
        }

    }
}
