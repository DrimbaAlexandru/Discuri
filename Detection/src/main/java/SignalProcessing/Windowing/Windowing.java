package SignalProcessing.Windowing;

import java.util.function.Function;

/**
 * Created by Alex on 15.11.2017.
 */
public class Windowing
{
    public static void apply( double[] samples, int len, Function< Double, Double > window_function )
    {
        if( len == 0 )
        {
            return;
        }
        int i;
        samples[ 0 ] *= window_function.apply( 0.0 );
        for( i = 1; i < len; i++ )
        {
            samples[ i ] *= window_function.apply( ( double )i / ( len - 1 ) );
        }
    }

    public final static Function< Double, Double > fade_in_window = ( x -> x );
    public final static Function< Double, Double > fade_out_window = ( x -> 1 - x );
    public final static Function< Double, Double > half_cosine_window = ( x -> ( -Math.cos( x * Math.PI ) + 1 ) / 2 );
    public final static Function< Double, Double > exponential_window = ( x -> Math.exp( x - 1 ) );
    public final static Function< Double, Double > cos_sq_window = ( x -> Math.pow( Math.cos( x * Math.PI / 2 ), 2 ) );
    public final static Function< Double, Double > inv_cos_sq_window = ( x -> 1 - Math.pow( Math.cos( x * Math.PI / 2 ), 2 ) );
    public final static Function< Double, Double > zero_window = ( x -> 0.0 );
    public final static Function< Double, Double > one_window = ( x -> 1.0 );
    public final static Function< Double, Double > Hann_window = ( x -> Math.pow( Math.sin( Math.PI * x ), 2 ) );
    public final static Function< Double, Double > Hamming_window = ( x -> 0.54 - 0.46 * Math.cos( 2 * Math.PI * x ) );
    public final static Function< Double, Double > Hanning_window = ( x -> 0.5 - 0.5 * Math.cos( 2 * Math.PI * x ) );
    public final static Function< Double, Double > Blackman_window = ( x -> 0.42 - 0.5 * Math.cos( 2 * Math.PI * x ) + 0.08 * Math.cos( 4 * Math.PI * x ) );
}
