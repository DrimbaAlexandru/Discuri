package Utils;

import static java.lang.Math.PI;

/**
 * Created by Alex on 28.11.2017.
 */
public class Complex
{
    private float r, i;

    public Complex copy()
    {
        return new Complex( r, i );
    }

    public Complex()
    {
        r = 0;
        i = 0;
    }

    public Complex( float re, float im )
    {
        r = re;
        i = im;
    }

    public void set( float re, float im )
    {
        r = re;
        i = im;
    }

    public Complex add( Complex other )
    {
        r += other.r;
        i += other.i;
        return this;
    }

    public Complex sub( Complex other )
    {
        r -= other.r;
        i -= other.i;
        return this;
    }

    public Complex mul( Complex other )
    {
        set( r * other.r - i * other.i, i * other.r + r * other.i );
        return this;
    }

    public Complex mul( float real)
    {
        set( real * r, real * i );
        return this;
    }

    public Complex inc( int n, int N )
    {
        set( ( float )( r * ( Math.cos( -2 * PI * n / N ) ) - i * ( Math.sin( -2 * PI * n / N ) ) ), ( float )( r * ( Math.sin( -2 * PI * n / N ) ) + i * ( Math.cos( -2 * PI * n / N ) ) ) );
        return this;
    }

    public float r()
    {
        return r;
    }

    public float i()
    {
        return i;
    }

    public static Complex fromAmplPhase( float A, float theta )
    {
        return new Complex( ( float )( A * Math.sin( theta ) ), ( float )( A * Math.cos( theta ) ) );
    }

    public float Ampl()
    {
        return ( float )( Math.sqrt( r * r + i * i ) );
    }

    public float sinTheta()
    {
        if( i == 0 )
        {
            return ( float )( PI / 2 );
        }
        else
        {
            return ( float )( Math.atan( r / i ) );
        }
    }

    @Override
    public String toString()
    {
        return r + " + " + i + "i";
    }
}
