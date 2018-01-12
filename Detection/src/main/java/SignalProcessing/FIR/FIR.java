package SignalProcessing.FIR;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import SignalProcessing.FourierTransforms.Fourier;
import SignalProcessing.Windowing.Windowing;
import Utils.Complex;

/**
 * Created by Alex on 24.11.2017.
 */
public class FIR
{
    private int tap_nr;
    private double[] b;

    public void apply( double[] x, int N ) throws DataSourceException
    {
        if( x.length < N + tap_nr - 1 )
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
                ybuf[ buf_idx ] += b[ tap_nr - 1 - j ] * x[ i - j ];
            }
            buf_idx++;
        }

        for( i = tap_nr; i < N + tap_nr - 1; i++ )
        {
            if( buf_idx == tap_nr )
            {
                buf_idx = 0;
            }
            x[ i - tap_nr ] = ybuf[ buf_idx ];
            ybuf[ buf_idx ] = 0;
            for( j = Math.max( 0, i - N + 1 ); j < tap_nr; j++ ) // b[j]
            {
                ybuf[ buf_idx ] += b[ tap_nr - 1 - j ] * x[ i - j ];
            }
            buf_idx++;
        }

        for( i = N - 1; i < N + tap_nr - 1; i++ )
        {
            if( buf_idx == tap_nr )
            {
                buf_idx = 0;
            }
            x[ i ] = ybuf[ buf_idx ];
            buf_idx++;
        }
    }

    public void apply_FIR( double[] x, int N ) throws DataSourceException
    {
        if( x.length < N + ( tap_nr - 1 ) * 2 )
        {
            throw new DataSourceException( "Supplied array does not have the expected size to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i, j;
        double newVal;
        for( i = N + tap_nr - 2; i >= tap_nr - 1; i-- )
        {
            newVal = 0;
            for( j = 0; j < tap_nr; j++ ) // b[j]
            {
                newVal += b[ j ] * x[ i - j ];
            }
            x[ i ] = newVal;
        }
    }

    public void apply_IIR( double[] x, int N ) throws DataSourceException
    {
        if( x.length < N + ( tap_nr - 1 ) * 2 )
        {
            throw new DataSourceException( "Supplied array does not have the expected size to properly apply the filter", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i, j;
        double newVal;
        for( i = tap_nr - 1; i <= N + tap_nr - 2; i++ )
        {
            newVal = 0;
            for( j = 0; j < tap_nr; j++ )
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
        double filter[] = new double[ NyqFreq * 2 + 1 ];
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
        for( i = 0; i < NyqFreq * 2; i++ )
        {
            filter[ i ] = frq[ i ].r();
        }
        filter[ NyqFreq * 2 ] = frq[ 0 ].r();
        return new FIR( filter, NyqFreq * 2 +1);
    }

    public int getTap_nr()
    {
        return tap_nr;
    }
}
