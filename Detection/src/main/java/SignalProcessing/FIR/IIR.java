package SignalProcessing.FIR;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import SignalProcessing.FourierTransforms.Fourier;
import Utils.Complex;

/**
 * Created by Alex on 22.01.2018.
 */
public class IIR
{
    private int ff_coeff_nr;    // P+1 in formula
    private double[] ff;        // b in formula
    private int fb_coeff_nr;    // Q+1 in formula
    private double[] fb;        // a in formula

    public void apply( double[] x, int N ) throws DataSourceException
    {
        FIR fir = new FIR( ff, ff_coeff_nr );
        int i, j;
        double newVal;

        fir.apply( x, N );

        for( i = 0; i < N; i++ )
        {
            newVal = x[ i ];
            for( j = 1; j < Math.min( i + 1, fb_coeff_nr ); j++ )
            {
                newVal -= fb[ j ] * x[ i - j ];
            }
            newVal *= fb[ 0 ];
            x[ i ] = newVal;
        }

    }

    public IIR( double ff_coeffs[], int ff_tap_nr, double fb_coeffs[], int fb_tap_nr )
    {
        ff = new double[ ff_tap_nr ];
        ff_coeff_nr = ff_tap_nr;
        System.arraycopy( ff_coeffs, 0, ff, 0, ff_tap_nr );

        fb = new double[ fb_tap_nr ];
        fb_coeff_nr = fb_tap_nr;
        System.arraycopy( fb_coeffs, 0, fb, 0, fb_tap_nr );
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
        return new FIR( filter, NyqFreq * 2 + 1 );
    }

    public int getFf_coeff_nr()
    {
        return ff_coeff_nr;
    }
}
