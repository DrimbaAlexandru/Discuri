package SignalProcessing.FourierTransforms;

import Utils.Complex;

/**
 * Created by Alex on 28.11.2017.
 */
public class Fourier
{
    private static void incrucisare( Complex v1, Complex v2, int n, int N )
    {
        Complex c0 = v1.copy();
        v1.add( v2.copy().inc( n, N ) );
        v2.inc( n, N ).mul( new Complex( -1, 0 ) ).add( c0 );
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
            BUF[ i ] = new Complex( x[ lista_pozitii[ i ] ].r(), x[ lista_pozitii[ i ] ].i() );
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
            x[ i ].set( x[ i ].r(), -x[ i ].i() );
        }
        x = FFT( x, N );
        for( i = 0; i < N; i++ )
        {
            x[ i ].set( x[ i ].r() * N, -x[ i ].i() * N );
        }
        return x;
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
}
