package Utils;

import static java.lang.Math.PI;

/**
 * Created by Alex on 28.11.2017.
 */
public class Complex
{
    private double r, i;

    public Complex copy()
    {
        return new Complex( r, i );
    }

    public Complex()
    {
        r = 0;
        i = 0;
    }

    public Complex( double re, double im )
    {
        r = re;
        i = im;
    }

    public void set( double re, double im )
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

    public Complex mul( Complex other )
    {
        set( r * other.r - i * other.i, i * other.r + r * other.i );
        return this;
    }

    public Complex inc( int n, int N )
    {
        set( r * ( Math.cos( -2 * PI * n / N ) ) - i * ( Math.sin( -2 * PI * n / N ) ), r * ( Math.sin( -2 * PI * n / N ) ) + i * ( Math.cos( -2 * PI * n / N ) ) );
        return this;
    }

    public double r()
    {
        return r;
    }

    public double i()
    {
        return i;
    }

    public static Complex fromAmplPhase( double A, double theta )
    {
        return new Complex( A * Math.sin( theta ), A * Math.cos( theta ) );
    }

    public double Ampl()
    {
        return Math.sqrt( r * r + i * i );
    }

    public double sinTheta()
    {
        if( i == 0 )
        {
            return PI/2;
        }
        else
        {
            return Math.atan( r / i );
        }
    }

    @Override
    public String toString()
    {
        return r + " + " + i + "i";
    }
}
