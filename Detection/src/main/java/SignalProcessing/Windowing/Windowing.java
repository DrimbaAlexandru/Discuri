package SignalProcessing.Windowing;

import net.jafama.FastMath;

import java.util.function.Function;

/**
 * Created by Alex on 15.11.2017.
 */
public class Windowing
{
    public static void apply( float[] samples, int start_offset, int len, Function< Float, Float > window_function )
    {
        if( len == 0 )
        {
            return;
        }
        int i;
        for( i = 0; i < len; i++ )
        {
            samples[ start_offset + i ] *= window_function.apply( ( float )i / ( len - 1 ) );
        }
    }
    public static void apply( float[] samples, int len, Function< Float, Float > window_function )
    {
        apply( samples, 0, len, window_function );
    }

    public final static Function< Float, Float > fade_in_window = ( x -> x );
    public final static Function< Float, Float > fade_out_window = ( x -> 1 - x );
    public final static Function< Float, Float > exponential_window = ( x -> ( float )( Math.exp( x - 1 ) ) );
    public final static Function< Float, Float > cos_sq_window = ( x -> ( float )( Math.pow( Math.cos( x * Math.PI / 2 ), 2 ) ) );
    public final static Function< Float, Float > inv_cos_sq_window = ( x -> ( float )( 1 - Math.pow( Math.cos( x * Math.PI / 2 ), 2 ) ) );
    public final static Function< Float, Float > zero_window = ( x -> 0.0f );
    public final static Function< Float, Float > one_window = ( x -> 1.0f );
    public final static Function< Float, Float > Hann_window =  ( x -> ( float )( 0.5f - 0.5f * FastMath.cos( 2 * Math.PI * x ) ) );
    public final static Function< Float, Float > half_Hann_window = ( x -> ( float )( 0.5f - 0.5f * FastMath.cos( Math.PI * x ) ) );
    public final static Function< Float, Float > Hamming_window = ( x -> ( float )( 0.54f - 0.46f * FastMath.cos( 2 * Math.PI * x ) ) );
    public final static Function< Float, Float > Blackman_window = ( x -> ( float )( 0.42f - 0.5f * FastMath.cos( 2 * Math.PI * x ) + 0.08f * FastMath.cos( 4 * Math.PI * x ) ) );
}
