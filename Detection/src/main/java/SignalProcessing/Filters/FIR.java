package SignalProcessing.Filters;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import SignalProcessing.FourierTransforms.Fourier;
import Utils.Complex;
import Utils.Interval;

/**
 * Created by Alex on 24.11.2017.
 */
public class FIR
{
    private int tap_nr;
    private double[] b;
/*
    public void apply_with_implicit_left_zero_padding( double[] x, int N ) throws DataSourceException
    {
        if( x.length < N )
        {
            throw new DataSourceException( "Supplied array does not have enough space to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i, j, buf_idx = 0;
        double[] ybuf = new double[ tap_nr ];

        for( i = 0; i < tap_nr; i++ ) // y[i]
        {
            ybuf[ buf_idx ] = 0;
            for( j = 0; j <= i; j++ ) // b[j]
            {
                ybuf[ buf_idx ] += b[ j ] * x[ i - j ];
            }
            buf_idx++;
        }

        for( i = tap_nr; i < N; i++ )
        {
            if( buf_idx == tap_nr )
            {
                buf_idx = 0;
            }
            x[ i - tap_nr ] = ybuf[ buf_idx ];
            ybuf[ buf_idx ] = 0;
            for( j = 0; j < tap_nr; j++ ) // b[j]
            {
                ybuf[ buf_idx ] += b[ j ] * x[ i - j ];
            }
            buf_idx++;
        }

        for( i = N - tap_nr; i < N; i++ )
        {
            if( buf_idx == tap_nr )
            {
                buf_idx = 0;
            }
            x[ i ] = ybuf[ buf_idx ];
            buf_idx++;
        }
    }
*/
    public void apply( double[] x, Interval range ) throws DataSourceException
    {

        if( x.length > range.r || range.l < 0 )
        {
            throw new DataSourceException( "Supplied array does not have the expected size to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i, j;
        double newVal;

        for( i = range.r - 1; i >= range.l; i-- )
        {
            newVal = 0;
            for( j = 0; j < Math.min( i + 1, tap_nr ); j++ ) // b[j]
            {
                newVal += b[ j ] * x[ i - j ];
            }
            x[ i ] = newVal;
        }
    }

    public FIR( double coeffs[], int n )
    {
        int i;
        b = new double[ n ];
        tap_nr = n;
        System.arraycopy( coeffs, 0, b, 0, n );
    }

    public static FIR fromFreqResponse( double[] x, int NyqFreq, int k )
    {
        Complex frq[] = new Complex[ NyqFreq * 2 ];
        double filter[] = new double[ NyqFreq + 1 ];
        int i;
        for( i = 0; i <= NyqFreq; i++ )
        {
            if( i % 2 == 0 )
            {
                frq[ i ] = new Complex( x[ i ], 0 );
            }
            else
            {
                frq[ i ] = new Complex( -x[ i ], 0 );
            }
        }
        for( i = NyqFreq + 1; i < NyqFreq * 2; i++ )
        {
            frq[ i ] = new Complex( 0, 0 );
        }
        frq = Fourier.IFFT( frq, NyqFreq * 2 );
        for( i = 0; i <= NyqFreq/2; i++ )
        {
            filter[ i ] = frq[ i * 2 ].r();
            filter[ NyqFreq - i ] = frq[ i * 2 ].r();
        }
        return new FIR( filter, NyqFreq + 1 );
    }

    public int getTap_nr()
    {
        return tap_nr;
    }

    public double[] getB()
    {
        return b;
    }
}
