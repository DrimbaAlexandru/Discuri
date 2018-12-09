package SignalProcessing.FourierTransforms;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import Utils.Complex;
import Utils.Util_Stuff;
import net.jafama.FastMath;

import static java.lang.Math.PI;

/**
 * Created by Alex on 28.11.2017.
 */
public class Fourier
{
    private static float[][] temp = new float[ 2 ][ 1 ];

    private static void incrucisare( Complex v1, Complex v2, int n, int N )
    {
        Complex c0 = v1.copy();
        v1.add( v2.copy().inc( n, N ) );
        v2.inc( n, N ).mul( new Complex( -1, 0 ) ).add( c0 );
    }

    private static int reverse_bits( int x, int bits )
    {
        int y = 0;
        x = x & ( ( 1 << bits ) - 1 );
        while( bits > 0 )
        {
            y <<= 1;
            y |= x & 1;
            x >>= 1;
            bits--;
        }
        return y;
    }

    public static void FFT_inplace( Complex[] x, int N ) throws DataSourceException
    {
        int i, j, k;
        final int logN = ( int )Util_Stuff.log( N, 2 );
        final float coeff = 1.0f / N;
        final Complex c2 = new Complex();
        final Complex twiddle_factor = new Complex();
        int i1, i2;

        if( !Util_Stuff.is_power_of_two( N ) )
        {
            throw new DataSourceException( "N must be a power of 2", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( x.length < N )
        {
            throw new DataSourceException( "Array size must be equal or larger than N", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( temp[ 0 ].length < N )
        {
            temp[ 0 ] = new float[ N ];
            temp[ 1 ] = new float[ N ];
        }

        for( i = 0; i < N; i++ )
        {
            i1 = reverse_bits( i, logN );
            temp[ 0 ][ i ] = x[ i1 ].r;
            temp[ 1 ][ i ] = x[ i1 ].i;
        }

        //Butterfly
        for( k = 1; k <= N / 2; k *= 2 )
        {
            for( i = 0; i < N; i += k * 2 )
            {
                for( j = 0; j < k; j++ )
                {
                    i1 = i + j;
                    i2 = i + j + k;

                    //twiddle_factor.r = ( float )FastMath.cos( -FastMath.PI * j / k );
                    //twiddle_factor.i = ( float )FastMath.sin( -FastMath.PI * j / k );

                    c2.r = temp[ 0 ][ i2 ];
                    temp[ 0 ][ i2 ] = temp[ 0 ][ i2 ] * twiddle_factor.r - temp[ 1 ][ i2 ] * twiddle_factor.i;
                    temp[ 1 ][ i2 ] = temp[ 1 ][ i2 ] * twiddle_factor.r + c2.r * twiddle_factor.i;

                    c2.r = temp[ 0 ][ i2 ];
                    c2.i = temp[ 1 ][ i2 ];

                    temp[ 0 ][ i2 ] = -temp[ 0 ][ i2 ] + temp[ 0 ][ i1 ];
                    temp[ 1 ][ i2 ] = -temp[ 1 ][ i2 ] + temp[ 1 ][ i1 ];

                    temp[ 0 ][ i1 ] += c2.r;
                    temp[ 1 ][ i1 ] += c2.i;
                }
            }
        }

        for( i = 0; i < N; i++ )
        {
            x[ i ].r = temp[ 0 ][ i ] * coeff;
            x[ i ].i = temp[ 1 ][ i ] * coeff;
        }
    }

    public static Complex[] FFT( Complex[] x, int N )
    {
        Complex BUF[] = new Complex[ N ];
        int i, j, k;
        int lista_pozitii[] = new int[ N ];
        lista_pozitii[ 0 ] = 0;
        int distanta;
        for( distanta = 1; distanta < N; distanta *= 2 )
        {
            for( i = distanta; i < distanta * 2; i++ )
            {
                lista_pozitii[ i ] = N / distanta / 2 + lista_pozitii[ i - distanta ];
            }
        }

        for( i = 0; i < N; i++ )
        {
            BUF[ i ] = new Complex( x[ lista_pozitii[ i ] ].r, x[ lista_pozitii[ i ] ].i );
        }

        //Butterfly
        for( k = 1; k < N; k *= 2 )
        {
            for( i = 0; i < N / k / 2; i++ )
            {
                for( j = 0; j < k; j++ )
                {
                    incrucisare( BUF[ i * k * 2 + j ], BUF[ i * k * 2 + j + k ], j, k * 2 );
                }
            }
        }
        Complex c = new Complex( ( 1.0f / N ), 0 );
        for( i = 0; i < N; i++ )
        {
            BUF[ i ].mul( c );
        }

        return BUF;
    }

    public static Complex[] IFFT( Complex[] x, int N )
    {
        int i;
        for( i = 0; i < N; i++ )
        {
            x[ i ].i *= -1;
        }
        x = FFT( x, N );
        for( i = 0; i < N; i++ )
        {
            x[ i ].i *= -N;
            x[ i ].r *= N;
        }
        return x;
    }

    public static void IFFT_inplace( Complex[] x, int N ) throws DataSourceException
    {
        int i;

        if( x.length < N )
        {
            throw new DataSourceException( "Array size must be equal or larger than N", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        for( i = 0; i < N; i++ )
        {
            x[ i ].i *= -1;
        }
        FFT_inplace( x, N );
        for( i = 0; i < N; i++ )
        {
            x[ i ].i *= -N;
            x[ i ].r *= N;
        }
    }

    public static Complex one_freq_component( float[] signal, int start_offset, int length, float frequency, int sample_rate )
    {
        float period = ( float )( Math.PI * 2 / ( sample_rate / frequency ) );
        float cos_comp = 0, sin_comp = 0;
        int i;
        for( i = 0; i < length; i++ )
        {
            cos_comp += signal[ start_offset + i ] * Math.cos( i * period );
            sin_comp += signal[ start_offset + i ] * Math.sin( i * period );
        }
        return new Complex( cos_comp, sin_comp );
    }

    public static float IDFT( float x, Complex[] f, int N )
    {
        float value = 0;
        int i;
        for( i = 0; i < N; i++ )
        {
            value += f[ i ].i * Math.sin( -2 * Math.PI * i * x / N ) + f[ i ].r * Math.cos( -2 * Math.PI * i * x / N );
        }
        return value;
    }
}
