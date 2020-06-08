package SignalProcessing.FourierTransforms;

import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import Utils.DataTypes.Complex;
import Utils.Util_Stuff;
import net.jafama.FastMath;

/**
 * Created by Alex on 28.11.2017.
 */
public class Fourier
{
    private static float[][] sin_cos_table = new float[ 2 ][ 1 ];
    private static int sin_cos_table_length = 1;

    private static int[] bit_reversal_table = new int[ 1 ];
    private static int bit_reversal_table_length = 1;

    private static void incrucisare( Complex v1, Complex v2, int n, int N )
    {
        Complex c0 = v1.copy();
        v1.add( v2.copy().inc( n, N ) );
        v2.inc( n, N ).mul( new Complex( -1, 0 ) ).add( c0 );
    }

    private static void prepare_sin_cos_table( int N )
    {
        int i;
        if( sin_cos_table_length == N / 2 )
        {
            return;
        }
        if( sin_cos_table[ 0 ].length < N / 2 )
        {
            sin_cos_table = new float[ 2 ][ N / 2 ];
        }
        for( i = 0; i < N / 2; i++ )
        {
            sin_cos_table[ 0 ][ i ] = ( float )FastMath.sin( -FastMath.PI * i / ( N / 2 ) );
            sin_cos_table[ 1 ][ i ] = ( float )FastMath.cos( -FastMath.PI * i / ( N / 2 ) );
        }
        sin_cos_table_length = N / 2;
    }

    private static void prepare_bit_reversal_table( int N )
    {
        int i;
        final int logN = ( int )Util_Stuff.log( N, 2 );

        if( bit_reversal_table_length == N )
        {
            return;
        }
        if( bit_reversal_table.length < N )
        {
            bit_reversal_table = new int[ N ];
        }
        for( i = 0; i < N; i++ )
        {
            bit_reversal_table[ i ] = reverse_bits( i, logN );
        }
        bit_reversal_table_length = N;
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

    public static void FFT_inplace( float[] r_part, float[] i_part, int N ) throws DataSourceException
    {
        int i, j, k;
        final float coeff = 1.0f / N;
        float aux_r, aux_i;
        int idx_1, idx_2;
        int Npk;

        if( !Util_Stuff.is_power_of_two( N ) )
        {
            throw new DataSourceException( "N must be a power of 2", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( r_part.length < N || i_part.length < N )
        {
            throw new DataSourceException( "Array size must be equal or larger than N", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        prepare_bit_reversal_table( N );

        for( i = 0; i < N; i++ )
        {
            if( i < bit_reversal_table[ i ] )
            {
                aux_r = r_part[ i ];
                aux_i = i_part[ i ];
                r_part[ i ] = r_part[ bit_reversal_table[ i ] ];
                i_part[ i ] = i_part[ bit_reversal_table[ i ] ];
                r_part[ bit_reversal_table[ i ] ] = aux_r;
                i_part[ bit_reversal_table[ i ] ] = aux_i;
            }
        }

        prepare_sin_cos_table( N );

        //Butterfly
        for( k = 1; k <= N / 2; k *= 2 )
        {
            Npk = ( N / 2 ) / k;
            for( i = 0; i < N; i += k * 2 )
            {
                for( j = 0; j < k; j++ )
                {
                    idx_1 = i + j;
                    idx_2 = i + j + k;

                    aux_r = r_part[ idx_2 ];
                    r_part[ idx_2 ] = r_part[ idx_2 ] * sin_cos_table[ 1 ][ j * Npk ] - i_part[ idx_2 ] * sin_cos_table[ 0 ][ j * Npk ];
                    i_part[ idx_2 ] = i_part[ idx_2 ] * sin_cos_table[ 1 ][ j * Npk ] + aux_r * sin_cos_table[ 0 ][ j * Npk ];

                    aux_r = r_part[ idx_2 ];
                    aux_i = i_part[ idx_2 ];

                    r_part[ idx_2 ] = -r_part[ idx_2 ] + r_part[ idx_1 ];
                    i_part[ idx_2 ] = -i_part[ idx_2 ] + i_part[ idx_1 ];

                    r_part[ idx_1 ] += aux_r;
                    i_part[ idx_1 ] += aux_i;
                }
            }
        }
        for( i = 0; i < N; i++ )
        {
            r_part[ i ] *= coeff;
            i_part[ i ] *= coeff;
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

    public static void IFFT_inplace( float[] r_part, float[] i_part, int N ) throws DataSourceException
    {
        int i;

        if( r_part.length < N || i_part.length < N )
        {
            throw new DataSourceException( "Array size must be equal or larger than N", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        for( i = 0; i < N; i++ )
        {
            i_part[ i ] *= -1;
        }
        FFT_inplace( r_part, i_part, N );
        for( i = 0; i < N; i++ )
        {
            i_part[ i ] *= -N;
            r_part[ i ] *= N;
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
