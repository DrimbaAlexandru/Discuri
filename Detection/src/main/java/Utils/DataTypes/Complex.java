package Utils.DataTypes;

import static java.lang.Math.PI;

/**
 * Created by Alex on 28.11.2017.
 */
public class Complex
{
    public float r, i;

    private static Complex temp = new Complex();

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
        temp.r = this.r;
        this.r = r * other.r - i * other.i;
        this.i = i * other.r + temp.r * other.i;
        return this;
    }

    public Complex mul( float real )
    {
        this.r *= real;
        this.i *= real;
        return this;
    }

    public Complex inc( int n, int N )
    {
        temp.r = this.r;
        this.r = ( float )( r * ( Math.cos( -2 * PI * n / N ) ) - i * ( Math.sin( -2 * PI * n / N ) ) );
        this.i = ( float )( temp.r * ( Math.sin( -2 * PI * n / N ) ) + i * ( Math.cos( -2 * PI * n / N ) ) );
        return this;
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
