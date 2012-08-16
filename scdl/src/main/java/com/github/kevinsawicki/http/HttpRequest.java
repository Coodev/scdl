/*
 * Copyright (c) 2011 Kevin Sawicki <kevinsawicki@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.github.kevinsawicki.http;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import net.rdrei.android.scdl2.api.URLWrapper;

/**
 * A fluid interface for making HTTP requests using an underlying
 * {@link HttpURLConnection} (or sub-class).
 * <p>
 * Each instance supports making a single request and cannot be reused for
 * further requests.
 */
public class HttpRequest {

	/**
	 * 'UTF-8' charset name
	 */
	public static final String CHARSET_UTF8 = "UTF-8";

	/**
	 * 'Accept' header name
	 */
	public static final String HEADER_ACCEPT = "Accept";

	/**
	 * 'Accept-Charset' header name
	 */
	public static final String HEADER_ACCEPT_CHARSET = "Accept-Charset";

	/**
	 * 'Accept-Encoding' header name
	 */
	public static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";

	/**
	 * 'Authorization' header name
	 */
	public static final String HEADER_AUTHORIZATION = "Authorization";

	/**
	 * 'Cache-Control' header name
	 */
	public static final String HEADER_CACHE_CONTROL = "Cache-Control";

	/**
	 * 'Content-Encoding' header name
	 */
	public static final String HEADER_CONTENT_ENCODING = "Content-Encoding";

	/**
	 * 'Content-Length' header name
	 */
	public static final String HEADER_CONTENT_LENGTH = "Content-Length";

	/**
	 * 'Content-Type' header name
	 */
	public static final String HEADER_CONTENT_TYPE = "Content-Type";

	/**
	 * 'Date' header name
	 */
	public static final String HEADER_DATE = "Date";

	/**
	 * 'ETag' header name
	 */
	public static final String HEADER_ETAG = "ETag";

	/**
	 * 'Expires' header name
	 */
	public static final String HEADER_EXPIRES = "Expires";

	/**
	 * 'If-None-Match' header name
	 */
	public static final String HEADER_IF_NONE_MATCH = "If-None-Match";

	/**
	 * 'Last-Modified' header name
	 */
	public static final String HEADER_LAST_MODIFIED = "Last-Modified";

	/**
	 * 'Location' header name
	 */
	public static final String HEADER_LOCATION = "Location";

	/**
	 * 'Server' header name
	 */
	public static final String HEADER_SERVER = "Server";

	/**
	 * 'User-Agent' header name
	 */
	public static final String HEADER_USER_AGENT = "User-Agent";

	/**
	 * 'DELETE' request method
	 */
	public static final String METHOD_DELETE = "DELETE";

	/**
	 * 'GET' request method
	 */
	public static final String METHOD_GET = "GET";

	/**
	 * 'HEAD' request method
	 */
	public static final String METHOD_HEAD = "HEAD";

	/**
	 * 'OPTIONS' options method
	 */
	public static final String METHOD_OPTIONS = "OPTIONS";

	/**
	 * 'POST' request method
	 */
	public static final String METHOD_POST = "POST";

	/**
	 * 'PUT' request method
	 */
	public static final String METHOD_PUT = "PUT";

	/**
	 * 'TRACE' request method
	 */
	public static final String METHOD_TRACE = "TRACE";

	/**
	 * 'charset' header value parameter
	 */
	public static final String PARAM_CHARSET = "charset";

	private static final String BOUNDARY = "00content0boundary00";

	private static final String CONTENT_TYPE_MULTIPART = "multipart/form-data; boundary="
			+ BOUNDARY;

	private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

	/**
	 * <p>
	 * Encodes and decodes to and from Base64 notation.
	 * </p>
	 * <p>
	 * I am placing this code in the Public Domain. Do with it as you will. This
	 * software comes with no guarantees or warranties but with plenty of
	 * well-wishing instead! Please visit <a
	 * href="http://iharder.net/base64">http://iharder.net/base64</a>
	 * periodically to check for updates or to contribute improvements.
	 * </p>
	 * 
	 * @author Robert Harder
	 * @author rob@iharder.net
	 * @version 2.3.7
	 */
	public static class Base64 {

		/** The equals sign (=) as a byte. */
		private static final byte EQUALS_SIGN = (byte) '=';

		/** Preferred encoding. */
		private static final String PREFERRED_ENCODING = "US-ASCII";

		/** The 64 valid Base64 values. */
		private static final byte[] _STANDARD_ALPHABET = { (byte) 'A',
				(byte) 'B', (byte) 'C', (byte) 'D', (byte) 'E', (byte) 'F',
				(byte) 'G', (byte) 'H', (byte) 'I', (byte) 'J', (byte) 'K',
				(byte) 'L', (byte) 'M', (byte) 'N', (byte) 'O', (byte) 'P',
				(byte) 'Q', (byte) 'R', (byte) 'S', (byte) 'T', (byte) 'U',
				(byte) 'V', (byte) 'W', (byte) 'X', (byte) 'Y', (byte) 'Z',
				(byte) 'a', (byte) 'b', (byte) 'c', (byte) 'd', (byte) 'e',
				(byte) 'f', (byte) 'g', (byte) 'h', (byte) 'i', (byte) 'j',
				(byte) 'k', (byte) 'l', (byte) 'm', (byte) 'n', (byte) 'o',
				(byte) 'p', (byte) 'q', (byte) 'r', (byte) 's', (byte) 't',
				(byte) 'u', (byte) 'v', (byte) 'w', (byte) 'x', (byte) 'y',
				(byte) 'z', (byte) '0', (byte) '1', (byte) '2', (byte) '3',
				(byte) '4', (byte) '5', (byte) '6', (byte) '7', (byte) '8',
				(byte) '9', (byte) '+', (byte) '/' };

		/** Defeats instantiation. */
		private Base64() {
		}

		/**
		 * <p>
		 * Encodes up to three bytes of the array <var>source</var> and writes
		 * the resulting four Base64 bytes to <var>destination</var>. The source
		 * and destination arrays can be manipulated anywhere along their length
		 * by specifying <var>srcOffset</var> and <var>destOffset</var>. This
		 * method does not check to make sure your arrays are large enough to
		 * accomodate <var>srcOffset</var> + 3 for the <var>source</var> array
		 * or <var>destOffset</var> + 4 for the <var>destination</var> array.
		 * The actual number of significant bytes in your array is given by
		 * <var>numSigBytes</var>.
		 * </p>
		 * <p>
		 * This is the lowest level of the encoding methods with all possible
		 * parameters.
		 * </p>
		 * 
		 * @param source
		 *            the array to convert
		 * @param srcOffset
		 *            the index where conversion begins
		 * @param numSigBytes
		 *            the number of significant bytes in your array
		 * @param destination
		 *            the array to hold the conversion
		 * @param destOffset
		 *            the index where output will be put
		 * @return the <var>destination</var> array
		 * @since 1.3
		 */
		private static byte[] encode3to4(byte[] source, int srcOffset,
				int numSigBytes, byte[] destination, int destOffset) {

			byte[] ALPHABET = _STANDARD_ALPHABET;

			int inBuff = ((numSigBytes > 0) ? ((source[srcOffset] << 24) >>> 8)
					: 0)
					| ((numSigBytes > 1) ? ((source[srcOffset + 1] << 24) >>> 16)
							: 0)
					| ((numSigBytes > 2) ? ((source[srcOffset + 2] << 24) >>> 24)
							: 0);

			switch (numSigBytes) {
			case 3:
				destination[destOffset] = ALPHABET[(inBuff >>> 18)];
				destination[destOffset + 1] = ALPHABET[(inBuff >>> 12) & 0x3f];
				destination[destOffset + 2] = ALPHABET[(inBuff >>> 6) & 0x3f];
				destination[destOffset + 3] = ALPHABET[(inBuff) & 0x3f];
				return destination;

			case 2:
				destination[destOffset] = ALPHABET[(inBuff >>> 18)];
				destination[destOffset + 1] = ALPHABET[(inBuff >>> 12) & 0x3f];
				destination[destOffset + 2] = ALPHABET[(inBuff >>> 6) & 0x3f];
				destination[destOffset + 3] = EQUALS_SIGN;
				return destination;

			case 1:
				destination[destOffset] = ALPHABET[(inBuff >>> 18)];
				destination[destOffset + 1] = ALPHABET[(inBuff >>> 12) & 0x3f];
				destination[destOffset + 2] = EQUALS_SIGN;
				destination[destOffset + 3] = EQUALS_SIGN;
				return destination;

			default:
				return destination;
			}
		}

		/**
		 * Encode string as a byte array in Base64 annotation.
		 * 
		 * @param string
		 * @return The Base64-encoded data as a string
		 */
		public static String encode(String string) {
			byte[] bytes;
			try {
				bytes = string.getBytes(PREFERRED_ENCODING);
			} catch (UnsupportedEncodingException e) {
				bytes = string.getBytes();
			}
			return encodeBytes(bytes);
		}

		/**
		 * Encodes a byte array into Base64 notation.
		 * 
		 * @param source
		 *            The data to convert
		 * @return The Base64-encoded data as a String
		 * @throws NullPointerException
		 *             if source array is null
		 * @throws IllegalArgumentException
		 *             if source array, offset, or length are invalid
		 * @since 2.0
		 */
		public static String encodeBytes(byte[] source) {
			return encodeBytes(source, 0, source.length);
		}

		/**
		 * Encodes a byte array into Base64 notation.
		 * 
		 * @param source
		 *            The data to convert
		 * @param off
		 *            Offset in array where conversion should begin
		 * @param len
		 *            Length of data to convert
		 * @return The Base64-encoded data as a String
		 * @throws NullPointerException
		 *             if source array is null
		 * @throws IllegalArgumentException
		 *             if source array, offset, or length are invalid
		 * @since 2.0
		 */
		public static String encodeBytes(byte[] source, int off, int len) {
			byte[] encoded = encodeBytesToBytes(source, off, len);
			try {
				return new String(encoded, PREFERRED_ENCODING);
			} catch (UnsupportedEncodingException uue) {
				return new String(encoded);
			}
		}

		/**
		 * Similar to {@link #encodeBytes(byte[], int, int)} but returns a byte
		 * array instead of instantiating a String. This is more efficient if
		 * you're working with I/O streams and have large data sets to encode.
		 * 
		 * 
		 * @param source
		 *            The data to convert
		 * @param off
		 *            Offset in array where conversion should begin
		 * @param len
		 *            Length of data to convert
		 * @return The Base64-encoded data as a String if there is an error
		 * @throws NullPointerException
		 *             if source array is null
		 * @throws IllegalArgumentException
		 *             if source array, offset, or length are invalid
		 * @since 2.3.1
		 */
		public static byte[] encodeBytesToBytes(byte[] source, int off, int len) {

			if (source == null)
				throw new NullPointerException("Cannot serialize a null array.");

			if (off < 0)
				throw new IllegalArgumentException(
						"Cannot have negative offset: " + off);

			if (len < 0)
				throw new IllegalArgumentException(
						"Cannot have length offset: " + len);

			if (off + len > source.length)
				throw new IllegalArgumentException(
						String.format(
								"Cannot have offset of %d and length of %d with array of length %d",
								off, len, source.length));

			// Bytes needed for actual encoding
			int encLen = (len / 3) * 4 + ((len % 3 > 0) ? 4 : 0);

			byte[] outBuff = new byte[encLen];

			int d = 0;
			int e = 0;
			int len2 = len - 2;
			for (; d < len2; d += 3, e += 4)
				encode3to4(source, d + off, 3, outBuff, e);

			if (d < len) {
				encode3to4(source, d + off, len - d, outBuff, e);
				e += 4;
			}

			if (e <= outBuff.length - 1) {
				byte[] finalOut = new byte[e];
				System.arraycopy(outBuff, 0, finalOut, 0, e);
				return finalOut;
			} else
				return outBuff;
		}
	}

	/**
	 * HTTP request exception whose cause is always an {@link IOException}
	 */
	public static class HttpRequestException extends RuntimeException {

		/** serialVersionUID */
		private static final long serialVersionUID = -1170466989781746231L;

		/**
		 * @param cause
		 */
		protected HttpRequestException(final IOException cause) {
			super(cause);
		}

		/**
		 * Get {@link IOException} that triggered this request exception
		 * 
		 * @return {@link IOException} cause
		 */
		public IOException getCause() {
			return (IOException) super.getCause();
		}
	}

	/**
	 * Operation that handles executing a callback once complete and handling
	 * nested exceptions
	 * 
	 * @param <V>
	 */
	protected abstract static class Operation<V> implements Callable<V> {

		/**
		 * Run operation
		 * 
		 * @return result
		 * @throws HttpRequestException
		 * @throws IOException
		 */
		protected abstract V run() throws HttpRequestException, IOException;

		/**
		 * Operation complete callback
		 * 
		 * @throws IOException
		 */
		protected abstract void done() throws IOException;

		public V call() throws HttpRequestException {
			boolean thrown = false;
			try {
				return run();
			} catch (HttpRequestException e) {
				thrown = true;
				throw e;
			} catch (IOException e) {
				thrown = true;
				throw new HttpRequestException(e);
			} finally {
				try {
					done();
				} catch (IOException e) {
					if (!thrown)
						throw new HttpRequestException(e);
				}
			}
		}
	}

	/**
	 * Class that ensures a {@link Closeable} gets closed with proper exception
	 * handling.
	 * 
	 * @param <V>
	 */
	protected abstract static class CloseOperation<V> extends Operation<V> {

		private final Closeable closeable;

		private final boolean ignoreCloseExceptions;

		/**
		 * Create closer for operation
		 * 
		 * @param closeable
		 * @param ignoreCloseExceptions
		 */
		protected CloseOperation(final Closeable closeable,
				final boolean ignoreCloseExceptions) {
			this.closeable = closeable;
			this.ignoreCloseExceptions = ignoreCloseExceptions;
		}

		protected void done() throws IOException {
			if (closeable instanceof Flushable)
				((Flushable) closeable).flush();
			if (ignoreCloseExceptions)
				try {
					closeable.close();
				} catch (IOException e) {
					// Ignored
				}
			else
				closeable.close();
		}
	}

	/**
	 * Class that and ensures a {@link Flushable} gets flushed with proper
	 * exception handling.
	 * 
	 * @param <V>
	 */
	protected abstract static class FlushOperation<V> extends Operation<V> {

		private final Flushable flushable;

		/**
		 * Create flush operation
		 * 
		 * @param flushable
		 */
		protected FlushOperation(final Flushable flushable) {
			this.flushable = flushable;
		}

		protected void done() throws IOException {
			flushable.flush();
		}
	}

	/**
	 * Request output stream
	 */
	public static class RequestOutputStream extends BufferedOutputStream {

		private final CharsetEncoder encoder;

		/**
		 * Create request output stream
		 * 
		 * @param stream
		 * @param charsetName
		 * @param bufferSize
		 */
		public RequestOutputStream(OutputStream stream, String charsetName,
				int bufferSize) {
			super(stream, bufferSize);
			if (charsetName == null)
				charsetName = CHARSET_UTF8;
			encoder = Charset.forName(charsetName).newEncoder();
		}

		/**
		 * Write string to stream
		 * 
		 * @param value
		 * @return this stream
		 * @throws IOException
		 */
		public RequestOutputStream write(final String value) throws IOException {
			final ByteBuffer bytes = encoder.encode(CharBuffer.wrap(value));
			super.write(bytes.array(), 0, bytes.limit());
			return this;
		}
	}

	/**
	 * Implement this interface to get updates about the current
	 * sending/receiving progress.
	 * 
	 * @author pascal
	 */
	public static interface SendCallback {
		void onSend(long sentBytes);
	}

	/**
	 * Start a 'GET' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest get(CharSequence url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_GET);
	}

	/**
	 * Start a 'GET' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest get(URL url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_GET);
	}

	/**
	 * Start a 'GET' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest get(URLWrapper url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_GET);
	}

	/**
	 * Start a 'POST' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest post(CharSequence url)
			throws HttpRequestException {
		return new HttpRequest(url, METHOD_POST);
	}

	/**
	 * Start a 'POST' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest post(URL url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_POST);
	}

	/**
	 * Start a 'POST' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest post(URLWrapper url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_POST);
	}

	/**
	 * Start a 'PUT' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest put(CharSequence url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_PUT);
	}

	/**
	 * Start a 'PUT' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest put(URL url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_PUT);
	}

	/**
	 * Start a 'PUT' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest put(URLWrapper url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_PUT);
	}

	/**
	 * Start a 'DELETE' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest delete(CharSequence url)
			throws HttpRequestException {
		return new HttpRequest(url, METHOD_DELETE);
	}

	/**
	 * Start a 'DELETE' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest delete(URL url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_DELETE);
	}

	/**
	 * Start a 'DELETE' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest delete(URLWrapper url)
			throws HttpRequestException {
		return new HttpRequest(url, METHOD_DELETE);
	}

	/**
	 * Start a 'HEAD' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest head(CharSequence url)
			throws HttpRequestException {
		return new HttpRequest(url, METHOD_HEAD);
	}

	/**
	 * Start a 'HEAD' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest head(URL url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_HEAD);
	}

	/**
	 * Start a 'HEAD' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest head(URLWrapper url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_HEAD);
	}

	/**
	 * Start a 'OPTIONS' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest options(CharSequence url)
			throws HttpRequestException {
		return new HttpRequest(url, METHOD_OPTIONS);
	}

	/**
	 * Start a 'OPTIONS' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest options(URL url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_OPTIONS);
	}

	/**
	 * Start a 'OPTIONS' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest options(URLWrapper url)
			throws HttpRequestException {
		return new HttpRequest(url, METHOD_OPTIONS);
	}

	/**
	 * Start a 'TRACE' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest trace(CharSequence url)
			throws HttpRequestException {
		return new HttpRequest(url, METHOD_TRACE);
	}

	/**
	 * Start a 'TRACE' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest trace(URL url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_TRACE);
	}

	/**
	 * Start a 'TRACE' request to the given URL
	 * 
	 * @param url
	 * @return request
	 * @throws HttpRequestException
	 */
	public static HttpRequest trace(URLWrapper url) throws HttpRequestException {
		return new HttpRequest(url, METHOD_TRACE);
	}

	/**
	 * Set the 'http.keepAlive' property to the given value.
	 * <p>
	 * This setting will apply to requests.
	 * 
	 * @param keepAlive
	 */
	public static void keepAlive(boolean keepAlive) {
		setProperty("http.keepAlive", Boolean.toString(keepAlive));
	}

	/**
	 * Set the 'http.proxyHost' & 'https.proxyHost' properties to the given
	 * value.
	 * <p>
	 * This setting will apply to requests.
	 * 
	 * @param host
	 */
	public static void proxyHost(final String host) {
		setProperty("http.proxyHost", host);
		setProperty("https.proxyHost", host);
	}

	/**
	 * Set the 'http.proxyPort' & 'https.proxyPort' properties to the given
	 * value.
	 * <p>
	 * This setting will apply to requests.
	 * 
	 * @param port
	 */
	public static void proxyPort(final int port) {
		final String portValue = Integer.toString(port);
		setProperty("http.proxyPort", portValue);
		setProperty("https.proxyPort", portValue);
	}

	/**
	 * Set the 'http.nonProxyHosts' properties to the given value. Hosts will be
	 * separated by a '|' character.
	 * <p>
	 * This setting will apply to requests.
	 * 
	 * @param hosts
	 */
	public static void nonProxyHosts(String... hosts) {
		if (hosts == null)
			hosts = new String[0];
		if (hosts.length > 0) {
			StringBuilder separated = new StringBuilder();
			int last = hosts.length - 1;
			for (int i = 0; i < last; i++)
				separated.append(hosts[i]).append('|');
			separated.append(hosts[last]);
			setProperty("http.nonProxyHosts", separated.toString());
		} else
			setProperty("http.nonProxyHosts", null);
	}

	/**
	 * Set property to given value.
	 * <p>
	 * Specifying a null value will cause the property to be cleared
	 * 
	 * @param name
	 * @param value
	 * @return previous value
	 */
	private static final String setProperty(final String name,
			final String value) {
		if (value != null)
			return AccessController
					.doPrivileged(new PrivilegedAction<String>() {

						public String run() {
							return System.setProperty(name, value);
						}
					});
		else
			return AccessController
					.doPrivileged(new PrivilegedAction<String>() {

						public String run() {
							return System.clearProperty(name);
						}
					});
	}

	private final HttpURLConnection connection;

	private RequestOutputStream output;

	private boolean multipart;

	private boolean form;

	private boolean ignoreCloseExceptions = true;

	private int bufferSize = 8192;

	private SendCallback sendCallback;

	/**
	 * Create HTTP connection wrapper
	 * 
	 * @param url
	 * @param method
	 * @throws HttpRequestException
	 */
	public HttpRequest(final CharSequence url, final String method)
			throws HttpRequestException {
		try {
			connection = (HttpURLConnection) new URL(url.toString())
					.openConnection();
			connection.setRequestMethod(method);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	/**
	 * Create HTTP connection wrapper
	 * 
	 * @param url
	 * @param method
	 * @throws HttpRequestException
	 */
	public HttpRequest(final URL url, final String method)
			throws HttpRequestException {
		try {
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(method);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	/**
	 * Create HTTP connection wrapper using URLwrapper.
	 * 
	 * @param url
	 * @param method
	 * @throws HttpRequestException
	 */
	public HttpRequest(final URLWrapper url, final String method)
			throws HttpRequestException {

		try {
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(method);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	public String toString() {
		return connection.getRequestMethod() + " " + connection.getURL();
	}

	/**
	 * Get underlying connection
	 * 
	 * @return connection
	 */
	public HttpURLConnection getConnection() {
		return connection;
	}

	/**
	 * Set whether or not to ignore exceptions that occur from calling
	 * {@link Closeable#close()}
	 * <p>
	 * The default value of this setting is <code>true</code>
	 * 
	 * @param ignore
	 * @return this request
	 */
	public HttpRequest ignoreCloseExceptions(final boolean ignore) {
		ignoreCloseExceptions = ignore;
		return this;
	}

	/**
	 * Get whether or not exceptions thrown by {@link Closeable#close()} are
	 * ignored
	 * 
	 * @return true if ignoring, false if throwing
	 */
	public boolean ignoreCloseExceptions() {
		return ignoreCloseExceptions;
	}

	/**
	 * Get the status code of the response
	 * 
	 * @return the response code
	 * @throws HttpRequestException
	 */
	public int code() throws HttpRequestException {
		try {
			closeOutput();
			return connection.getResponseCode();
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	/**
	 * Set the value of the given {@link AtomicInteger} to the status code of
	 * the response
	 * 
	 * @param output
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest code(final AtomicInteger output)
			throws HttpRequestException {
		output.set(code());
		return this;
	}

	/**
	 * Is the response code a 200 OK?
	 * 
	 * @return true if 200, false otherwise
	 * @throws HttpRequestException
	 */
	public boolean ok() throws HttpRequestException {
		return HTTP_OK == code();
	}

	/**
	 * Is the response code a 201 Created?
	 * 
	 * @return true if 201, false otherwise
	 * @throws HttpRequestException
	 */
	public boolean created() throws HttpRequestException {
		return HTTP_CREATED == code();
	}

	/**
	 * Is the response code a 500 Internal Server Error?
	 * 
	 * @return true if 500, false otherwise
	 * @throws HttpRequestException
	 */
	public boolean serverError() throws HttpRequestException {
		return HTTP_INTERNAL_ERROR == code();
	}

	/**
	 * Is the response code a 400 Bad Request?
	 * 
	 * @return true if 400, false otherwise
	 * @throws HttpRequestException
	 */
	public boolean badRequest() throws HttpRequestException {
		return HTTP_BAD_REQUEST == code();
	}

	/**
	 * Is the response code a 404 Not Found?
	 * 
	 * @return true if 404, false otherwise
	 * @throws HttpRequestException
	 */
	public boolean notFound() throws HttpRequestException {
		return HTTP_NOT_FOUND == code();
	}

	/**
	 * Is the response code a 304 Not Modified?
	 * 
	 * @return true if 304, false otherwise
	 * @throws HttpRequestException
	 */
	public boolean notModified() throws HttpRequestException {
		return HTTP_NOT_MODIFIED == code();
	}

	/**
	 * Get status message of the response
	 * 
	 * @return message
	 * @throws HttpRequestException
	 */
	public String message() throws HttpRequestException {
		try {
			closeOutput();
			return connection.getResponseMessage();
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	/**
	 * Disconnect the connection
	 * 
	 * @return this request
	 */
	public HttpRequest disconnect() {
		connection.disconnect();
		return this;
	}

	/**
	 * Set chunked streaming mode to the given size
	 * 
	 * @param size
	 * @return this request
	 */
	public HttpRequest chunk(final int size) {
		connection.setChunkedStreamingMode(size);
		return this;
	}

	/**
	 * Set the size used when buffering and copying between streams
	 * <p>
	 * This size all send and receive buffers created for both char and byte
	 * arrays
	 * 
	 * @param size
	 * @return this request
	 */
	public HttpRequest bufferSize(final int size) {
		if (size < 1)
			throw new IllegalArgumentException("Size must be greater than zero");
		bufferSize = size;
		return this;
	}

	/**
	 * Get the configured buffer size
	 * 
	 * @return buffer size
	 */
	public int bufferSize() {
		return bufferSize;
	}

	/**
	 * Create byte array output stream
	 * 
	 * @return stream
	 */
	protected ByteArrayOutputStream byteStream() {
		final int size = contentLength();
		if (size > 0)
			return new ByteArrayOutputStream(size);
		else
			return new ByteArrayOutputStream();
	}

	/**
	 * Get response as {@link String} in given character set
	 * <p>
	 * This will fall back to using the UTF-8 character set if the given charset
	 * is null
	 * 
	 * @param charset
	 * @return string
	 * @throws HttpRequestException
	 */
	public String body(final String charset) throws HttpRequestException {
		final ByteArrayOutputStream output = byteStream();
		try {
			copy(buffer(), output);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		try {
			return output.toString(charset != null ? charset : CHARSET_UTF8);
		} catch (UnsupportedEncodingException e) {
			throw new HttpRequestException(e);
		}
	}

	/**
	 * Get response as {@link String} using character set returned from
	 * {@link #charset()}
	 * 
	 * @return string
	 * @throws HttpRequestException
	 */
	public String body() throws HttpRequestException {
		return body(charset());
	}

	/**
	 * Get response as byte array
	 * 
	 * @return byte array
	 * @throws HttpRequestException
	 */
	public byte[] bytes() throws HttpRequestException {
		final ByteArrayOutputStream output = byteStream();
		try {
			copy(buffer(), output);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return output.toByteArray();
	}

	/**
	 * Get response in a buffered stream
	 * 
	 * @see #bufferSize(int)
	 * @return stream
	 * @throws HttpRequestException
	 */
	public BufferedInputStream buffer() throws HttpRequestException {
		return new BufferedInputStream(stream(), bufferSize);
	}

	/**
	 * Get stream to response body
	 * 
	 * @return stream
	 * @throws HttpRequestException
	 */
	public InputStream stream() throws HttpRequestException {
		if (code() < HTTP_BAD_REQUEST)
			try {
				return connection.getInputStream();
			} catch (IOException e) {
				throw new HttpRequestException(e);
			}
		else {
			InputStream stream = connection.getErrorStream();
			if (stream != null)
				return stream;
			try {
				return connection.getInputStream();
			} catch (IOException e) {
				throw new HttpRequestException(e);
			}
		}
	}

	/**
	 * Get reader to response body using given character set.
	 * <p>
	 * This will fall back to using the UTF-8 character set if the given charset
	 * is null
	 * 
	 * @param charset
	 * @return reader
	 * @throws HttpRequestException
	 */
	public InputStreamReader reader(final String charset)
			throws HttpRequestException {
		try {
			return new InputStreamReader(stream(), charset != null ? charset
					: CHARSET_UTF8);
		} catch (UnsupportedEncodingException e) {
			throw new HttpRequestException(e);
		}
	}

	/**
	 * Get reader to response body using the character set returned from
	 * {@link #charset()}
	 * 
	 * @return reader
	 * @throws HttpRequestException
	 */
	public InputStreamReader reader() throws HttpRequestException {
		return reader(charset());
	}

	/**
	 * Get buffered reader to response body using the given character set r and
	 * the configured buffer size
	 * 
	 * 
	 * @see #bufferSize(int)
	 * @param charset
	 * @return reader
	 * @throws HttpRequestException
	 */
	public BufferedReader bufferedReader(final String charset)
			throws HttpRequestException {
		return new BufferedReader(reader(charset), bufferSize);
	}

	/**
	 * Get buffered reader to response body using the character set returned
	 * from {@link #charset()} and the configured buffer size
	 * 
	 * @see #bufferSize(int)
	 * @return reader
	 * @throws HttpRequestException
	 */
	public BufferedReader bufferedReader() throws HttpRequestException {
		return bufferedReader(charset());
	}

	/**
	 * Stream response body to file
	 * 
	 * @param file
	 * @return this request
	 */
	public HttpRequest receive(final File file) {
		final OutputStream output;
		try {
			output = new BufferedOutputStream(new FileOutputStream(file),
					bufferSize);
		} catch (FileNotFoundException e) {
			throw new HttpRequestException(e);
		}
		return new CloseOperation<HttpRequest>(output, ignoreCloseExceptions) {

			protected HttpRequest run() throws HttpRequestException,
					IOException {
				return receive(output);
			}
		}.call();
	}

	/**
	 * Stream response to given output stream
	 * 
	 * @param output
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest receive(final OutputStream output)
			throws HttpRequestException {
		try {
			return copy(buffer(), output);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

	/**
	 * Stream response to given print stream
	 * 
	 * @param output
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest receive(final PrintStream output)
			throws HttpRequestException {
		return receive((OutputStream) output);
	}

	/**
	 * Receive response into the given appendable
	 * 
	 * @param appendable
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest receive(final Appendable appendable)
			throws HttpRequestException {
		final BufferedReader reader = new BufferedReader(reader(), bufferSize);
		return new CloseOperation<HttpRequest>(reader, ignoreCloseExceptions) {

			public HttpRequest run() throws IOException {
				final CharBuffer buffer = CharBuffer.allocate(bufferSize);
				int read;
				while ((read = reader.read(buffer)) != -1) {
					buffer.rewind();
					appendable.append(buffer, 0, read);
					buffer.rewind();
				}
				return HttpRequest.this;
			}
		}.call();
	}

	/**
	 * Receive response into the given writer
	 * 
	 * @param writer
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest receive(final Writer writer) throws HttpRequestException {
		final BufferedReader reader = new BufferedReader(reader(), bufferSize);
		return new CloseOperation<HttpRequest>(reader, ignoreCloseExceptions) {

			public HttpRequest run() throws IOException {
				return copy(reader, writer);
			}
		}.call();
	}

	/**
	 * Set read timeout on connection to given value
	 * 
	 * @param timeout
	 * @return this request
	 */
	public HttpRequest readTimeout(final int timeout) {
		connection.setReadTimeout(timeout);
		return this;
	}

	/**
	 * Set connect timeout on connection to given value
	 * 
	 * @param timeout
	 * @return this request
	 */
	public HttpRequest connectTimeout(final int timeout) {
		connection.setConnectTimeout(timeout);
		return this;
	}

	/**
	 * Set header name to given value
	 * 
	 * @param name
	 * @param value
	 * @return this request
	 */
	public HttpRequest header(final String name, final String value) {
		connection.setRequestProperty(name, value);
		return this;
	}

	/**
	 * Set header name to given value
	 * 
	 * @param name
	 * @param value
	 * @return this request
	 */
	public HttpRequest header(final String name, final Number value) {
		return header(name, value != null ? value.toString() : null);
	}

	/**
	 * Set header names to given values.
	 * <p>
	 * Each name should be followed by the corresponding value and the number of
	 * arguments must be divisible by 2.
	 * 
	 * @param headers
	 * @return this request
	 */
	public HttpRequest headers(final String... headers) {
		if (headers == null)
			throw new IllegalArgumentException("Headers cannot be null");
		final int length = headers.length;
		if (length == 0)
			throw new IllegalArgumentException("Headers cannot be empty");
		if (length % 2 != 0)
			throw new IllegalArgumentException(
					"Headers length must be divisible by two");

		for (int i = 0; i < length; i += 2)
			header(headers[i], headers[i + 1]);
		return this;
	}

	/**
	 * Get a response header
	 * 
	 * @param name
	 * @return response header
	 */
	public String header(final String name) {
		return connection.getHeaderField(name);
	}

	/**
	 * Get a date header from the response
	 * 
	 * @param name
	 * @return date, -1 on failures
	 */
	public long dateHeader(final String name) {
		return connection.getHeaderFieldDate(name, -1L);
	}

	/**
	 * Get an integer header from the response
	 * 
	 * @param name
	 * @return integer, -1 on failures
	 */
	public int intHeader(final String name) {
		return connection.getHeaderFieldInt(name, -1);
	}

	/**
	 * Get parameter value from header
	 * 
	 * @param value
	 * @param paramName
	 * @return parameter value or null if none
	 */
	protected String getParam(final String value, final String paramName) {
		if (value == null || value.length() == 0)
			return null;
		int postSemi = value.indexOf(';') + 1;
		if (postSemi == 0 || postSemi == value.length())
			return null;
		String[] params = value.substring(postSemi).split(";");
		for (String param : params) {
			String[] split = param.split("=");
			if (split.length != 2)
				continue;
			if (!paramName.equals(split[0].trim()))
				continue;

			String charset = split[1].trim();
			int length = charset.length();
			if (length == 0)
				continue;
			if (length > 2 && '"' == charset.charAt(0)
					&& '"' == charset.charAt(length - 1))
				charset = charset.substring(1, length - 1);
			return charset;
		}
		return null;
	}

	/**
	 * Get 'charset' parameter from 'Content-Type' response header
	 * 
	 * @return charset or null if none
	 */
	public String charset() {
		return getParam(contentType(), PARAM_CHARSET);
	}

	/**
	 * Set the 'User-Agent' header to given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest userAgent(final String value) {
		return header(HEADER_USER_AGENT, value);
	}

	/**
	 * Set value of {@link HttpURLConnection#setUseCaches(boolean)}
	 * 
	 * @param useCaches
	 * @return this request
	 */
	public HttpRequest useCaches(final boolean useCaches) {
		connection.setUseCaches(useCaches);
		return this;
	}

	/**
	 * Set the 'Accept-Encoding' header to given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest acceptEncoding(final String value) {
		return header(HEADER_ACCEPT_ENCODING, value);
	}

	/**
	 * Set the 'Accept-Charset' header to given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest acceptCharset(final String value) {
		return header(HEADER_ACCEPT_CHARSET, value);
	}

	/**
	 * Get the 'Content-Encoding' header from the response
	 * 
	 * @return this request
	 */
	public String contentEncoding() {
		return header(HEADER_CONTENT_ENCODING);
	}

	/**
	 * Get the 'Server' header from the response
	 * 
	 * @return server
	 */
	public String server() {
		return header(HEADER_SERVER);
	}

	/**
	 * Get the 'Date' header from the response
	 * 
	 * @return date value, -1 on failures
	 */
	public long date() {
		return dateHeader(HEADER_DATE);
	}

	/**
	 * Get the 'Cache-Control' header from the response
	 * 
	 * @return cache control
	 */
	public String cacheControl() {
		return header(HEADER_CACHE_CONTROL);
	}

	/**
	 * Get the 'ETag' header from the response
	 * 
	 * @return entity tag
	 */
	public String eTag() {
		return header(HEADER_ETAG);
	}

	/**
	 * Get the 'Expires' header from the response
	 * 
	 * @return expires value, -1 on failures
	 */
	public long expires() {
		return dateHeader(HEADER_EXPIRES);
	}

	/**
	 * Get the 'Last-Modified' header from the response
	 * 
	 * @return last modified value, -1 on failures
	 */
	public long lastModified() {
		return dateHeader(HEADER_LAST_MODIFIED);
	}

	/**
	 * Get the 'Location' header from the response
	 * 
	 * @return location
	 */
	public String location() {
		return header(HEADER_LOCATION);
	}

	/**
	 * Set a SendCallback that is triggered on uploads.
	 * 
	 * @param callback
	 * @return this request.
	 */
	public HttpRequest setSendCallback(SendCallback callback) {
		sendCallback = callback;
		return this;
	}

	/**
	 * Remove the SendCallback from the current instance.
	 * 
	 * @return this request.
	 */
	public HttpRequest removeSendCallback() {
		sendCallback = null;
		return this;
	}

	/**
	 * Set the 'Authorization' header to given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest authorization(final String value) {
		return header(HEADER_AUTHORIZATION, value);
	}

	/**
	 * Set the 'Authorization' header to given values in Basic authentication
	 * format
	 * 
	 * @param name
	 * @param password
	 * @return this request
	 */
	public HttpRequest basic(final String name, final String password) {
		return authorization("Basic " + Base64.encode(name + ':' + password));
	}

	/**
	 * Set the 'If-Modified-Since' request header to the given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest ifModifiedSince(final long value) {
		connection.setIfModifiedSince(value);
		return this;
	}

	/**
	 * Set the 'If-None-Match' request header to the given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest ifNoneMatch(final String value) {
		return header(HEADER_IF_NONE_MATCH, value);
	}

	/**
	 * Set the 'Content-Type' request header to the given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest contentType(final String value) {
		return contentType(value, null);
	}

	/**
	 * Set the 'Content-Type' request header to the given value and charset
	 * 
	 * @param value
	 * @param charset
	 * @return this request
	 */
	public HttpRequest contentType(final String value, final String charset) {
		if (charset != null) {
			final String separator = "; " + PARAM_CHARSET + '=';
			return header(HEADER_CONTENT_TYPE, value + separator + charset);
		} else
			return header(HEADER_CONTENT_TYPE, value);
	}

	/**
	 * Get the 'Content-Type' header from the response
	 * 
	 * @return response header value
	 */
	public String contentType() {
		return header(HEADER_CONTENT_TYPE);
	}

	/**
	 * Get the 'Content-Type' header from the response
	 * 
	 * @return response header value
	 */
	public int contentLength() {
		return intHeader(HEADER_CONTENT_LENGTH);
	}

	/**
	 * Set the 'Content-Length' request header to the given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest contentLength(final String value) {
		return contentLength(Integer.parseInt(value));
	}

	/**
	 * Set the 'Content-Length' request header to the given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest contentLength(final int value) {
		connection.setFixedLengthStreamingMode(value);
		return this;
	}

	/**
	 * Set the 'Accept' header to given value
	 * 
	 * @param value
	 * @return this request
	 */
	public HttpRequest accept(final String value) {
		return header(HEADER_ACCEPT, value);
	}

	/**
	 * Copy from input stream to output stream
	 * 
	 * @param input
	 * @param output
	 * @return this request
	 * @throws IOException
	 */
	protected HttpRequest copy(final InputStream input,
			final OutputStream output) throws IOException {
		return new CloseOperation<HttpRequest>(input, ignoreCloseExceptions) {

			public HttpRequest run() throws IOException {
				final byte[] buffer = new byte[bufferSize];
				int read;
				long totalBytes = 0;
				while ((read = input.read(buffer)) != -1) {
					output.write(buffer, 0, read);
					totalBytes += read;
					if (sendCallback != null) {
						sendCallback.onSend(totalBytes);
					}
				}
				return HttpRequest.this;
			}
		}.call();
	}

	/**
	 * Copy from reader to writer
	 * 
	 * @param input
	 * @param output
	 * @return this request
	 * @throws IOException
	 */
	protected HttpRequest copy(final Reader input, final Writer output)
			throws IOException {
		return new CloseOperation<HttpRequest>(input, ignoreCloseExceptions) {

			public HttpRequest run() throws IOException {
				final char[] buffer = new char[bufferSize];
				int read;
				long totalBytes = 0;
				while ((read = input.read(buffer)) != -1) {
					output.write(buffer, 0, read);
					totalBytes += read;

					if (sendCallback != null) {
						sendCallback.onSend(totalBytes);
					}
				}
				return HttpRequest.this;
			}
		}.call();
	}

	/**
	 * Close output stream
	 * 
	 * @return this request
	 * @throws HttpRequestException
	 * @throws IOException
	 */
	public HttpRequest closeOutput() throws IOException {
		if (output == null)
			return this;
		if (multipart)
			output.write("\r\n--" + BOUNDARY + "--\r\n");
		if (ignoreCloseExceptions)
			try {
				output.close();
			} catch (IOException ignored) {
				// Ignored
			}
		else
			output.close();
		output = null;
		return this;
	}

	/**
	 * Open output stream
	 * 
	 * @return this request
	 * @throws IOException
	 */
	protected HttpRequest openOutput() throws IOException {
		if (output != null)
			return this;
		connection.setDoOutput(true);
		final String charset = getParam(
				connection.getRequestProperty(HEADER_CONTENT_TYPE),
				PARAM_CHARSET);
		output = new RequestOutputStream(connection.getOutputStream(), charset,
				bufferSize);
		return this;
	}

	/**
	 * Start part of a multipart
	 * 
	 * @return this request
	 * @throws IOException
	 */
	protected HttpRequest startPart() throws IOException {
		if (!multipart) {
			multipart = true;
			contentType(CONTENT_TYPE_MULTIPART).openOutput();
			output.write("--" + BOUNDARY + "\r\n");
		} else
			output.write("\r\n--" + BOUNDARY + "\r\n");
		return this;
	}

	/**
	 * Write part header
	 * 
	 * @param name
	 * @param filename
	 * @return this request
	 * @throws IOException
	 */
	protected HttpRequest writePartHeader(final String name,
			final String filename, boolean last) throws IOException {
		final StringBuilder partBuffer = new StringBuilder();
		partBuffer.append("form-data; name=\"").append(name);
		if (filename != null)
			partBuffer.append("\"; filename=\"").append(filename);
		partBuffer.append('"');
		return partHeader("Content-Disposition", partBuffer.toString(), last);
	}

	/**
	 * Write a Content-Type header for a part.
	 * 
	 * @param type
	 * @return
	 */
	protected HttpRequest writePartContentType(final String type, boolean last) {
		return partHeader(HEADER_CONTENT_TYPE, type, last);
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param part
	 * @return this request
	 */
	public HttpRequest part(final String name, final String part) {
		return part(name, null, part);
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param filename
	 * @param part
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest part(final String name, final String filename,
			final String part) throws HttpRequestException {
		try {
			startPart();
			writePartHeader(name, filename, true);
			output.write(part);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return this;
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param part
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest part(final String name, final Number part)
			throws HttpRequestException {
		return part(name, null, part);
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param filename
	 * @param part
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest part(final String name, final String filename,
			final Number part) throws HttpRequestException {
		return part(name, filename, part != null ? part.toString() : null);
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param part
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest part(final String name, final File part)
			throws HttpRequestException {
		return part(name, part.getName(), part);
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param filename
	 * @param part
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest part(final String name, final String filename,
			final File part) throws HttpRequestException {
		final InputStream stream;
		try {
			stream = new BufferedInputStream(new FileInputStream(part));
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return part(name, filename, stream);
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param filename
	 * @param part
	 * @param contentType
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest part(final String name, final String filename,
			final File part, final String contentType)
			throws HttpRequestException {
		final InputStream stream;
		try {
			stream = new BufferedInputStream(new FileInputStream(part));
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return part(name, filename, stream, contentType);
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param part
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest part(final String name, final InputStream part)
			throws HttpRequestException {
		return part(name, null, part);
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param filename
	 * @param part
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest part(final String name, final String filename,
			final InputStream part) throws HttpRequestException {
		try {
			startPart();
			writePartHeader(name, filename, true);
			copy(part, output);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return this;
	}

	/**
	 * Write part of a multipart request to the request body
	 * 
	 * @param name
	 * @param filename
	 * @param part
	 * @param contentType
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest part(final String name, final String filename,
			final InputStream part, final String contentType)
			throws HttpRequestException {
		try {
			startPart();
			writePartHeader(name, filename, false);
			writePartContentType(contentType, true);
			copy(part, output);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return this;
	}

	/**
	 * Write a multipart header to the response body
	 * 
	 * @param name
	 * @param value
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest partHeader(String name, String value, boolean last)
			throws HttpRequestException {

		HttpRequest result = send(name).send(": ").send(value);

		if (last) {
			return result.send("\r\n\r\n");
		} else {
			return result.send("\r\n");
		}
	}

	/**
	 * Write contents of file to request body
	 * 
	 * @param input
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest send(final File input) throws HttpRequestException {
		final InputStream stream;
		try {
			stream = new BufferedInputStream(new FileInputStream(input));
		} catch (FileNotFoundException e) {
			throw new HttpRequestException(e);
		}
		return send(stream);
	}

	/**
	 * Write byte array to request body
	 * 
	 * @param input
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest send(final byte[] input) throws HttpRequestException {
		return send(new ByteArrayInputStream(input));
	}

	/**
	 * Write stream to request body
	 * <p>
	 * The given stream will be closed once sending completes
	 * 
	 * @param input
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest send(final InputStream input)
			throws HttpRequestException {
		try {
			openOutput();
			copy(input, output);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return this;
	}

	/**
	 * Write reader to request body
	 * <p>
	 * The given reader will be closed once sending completes
	 * 
	 * @param input
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest send(final Reader input) throws HttpRequestException {
		try {
			openOutput();
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		final Writer writer = new OutputStreamWriter(output,
				output.encoder.charset());
		return new FlushOperation<HttpRequest>(writer) {

			protected HttpRequest run() throws IOException {
				return copy(input, writer);
			}
		}.call();
	}

	/**
	 * Write string to request body
	 * <p>
	 * The charset configured via {@link #contentType(String)} will be used and
	 * UTF-8 will be used if it is unset.
	 * 
	 * @param value
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest send(final String value) throws HttpRequestException {
		try {
			openOutput();
			output.write(value);
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return this;
	}

	/**
	 * Write the values in the map as form data to the request body
	 * <p>
	 * The pairs specified will be URL-encoded in UTF-8 and sent with the
	 * 'application/x-www-form-urlencoded' content-type
	 * 
	 * @param values
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest form(final Map<?, ?> values) throws HttpRequestException {
		return form(values, CHARSET_UTF8);
	}

	/**
	 * Write the name/value pair as form data to the request body
	 * <p>
	 * The pair specified will be URL-encoded in UTF-8 and sent with the
	 * 'application/x-www-form-urlencoded' content-type
	 * 
	 * @param name
	 * @param value
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest form(final Object name, final Object value) {
		return form(name, value, CHARSET_UTF8);
	}

	/**
	 * Write the name/value pair as form data to the request body
	 * <p>
	 * The values specified will be URL-encoded and sent with the
	 * 'application/x-www-form-urlencoded' content-type
	 * 
	 * @param name
	 * @param value
	 * @param charset
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest form(final Object name, final Object value,
			final String charset) {
		return form(Collections.singletonMap(name, value), charset);
	}

	/**
	 * Write the values in the map as encoded form data to the request body
	 * 
	 * @param values
	 * @param charset
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest form(final Map<?, ?> values, final String charset)
			throws HttpRequestException {
		boolean first = !form;
		if (first) {
			contentType(CONTENT_TYPE_FORM, charset);
			form = true;
		}
		if (values.isEmpty())
			return this;
		final Set<?> set = values.entrySet();
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final Iterator<Entry> entries = (Iterator<Entry>) set.iterator();
		try {
			openOutput();
			@SuppressWarnings("rawtypes")
			Entry entry = entries.next();
			if (!first)
				output.write('&');
			output.write(URLEncoder.encode(entry.getKey().toString(), charset));
			output.write('=');
			Object value = entry.getValue();
			if (value != null)
				output.write(URLEncoder.encode(value.toString(), charset));
			while (entries.hasNext()) {
				entry = entries.next();
				output.write('&');
				output.write(URLEncoder.encode(entry.getKey().toString(),
						charset));
				output.write('=');
				value = entry.getValue();
				if (value != null)
					output.write(URLEncoder.encode(value.toString(), charset));
			}
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
		return this;
	}

	/**
	 * Configure HTTPS connection to trust all certificates
	 * <p>
	 * This method does nothing if the current request is not a HTTPS request
	 * 
	 * @return this request
	 * @throws HttpRequestException
	 */
	public HttpRequest trustAllCerts() throws HttpRequestException {
		if (!(connection instanceof HttpsURLConnection))
			return this;
		final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

			public void checkClientTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}
		} };
		final SSLContext context;
		try {
			context = SSLContext.getInstance("TLS");
			context.init(null, trustAllCerts, new SecureRandom());
		} catch (GeneralSecurityException e) {
			throw new HttpRequestException(new IOException(
					e.getLocalizedMessage()));
		}
		((HttpsURLConnection) connection).setSSLSocketFactory(context
				.getSocketFactory());
		return this;
	}

	/**
	 * Set up one or multiple trust managers for this connection.
	 * Does nothing if the current request is no SSL connection.
	 * 
	 * @param manager
	 * @return this instance.
	 */
	public HttpRequest applyTrustManager(TrustManager[] trustManagers) {
		if (!(connection instanceof HttpsURLConnection))
			return this;

		SSLContext sslContext;
		try {
			sslContext = SSLContext.getInstance("TLS");
		} catch (NoSuchAlgorithmException e) {
			// Again, should not happen if I didn't type it wrong.
			throw new IllegalArgumentException(e);
		}

		try {
			sslContext.init(null, trustManagers, null);
		} catch (KeyManagementException e) {
			throw new IllegalStateException(e);
		}

		((HttpsURLConnection) connection).setSSLSocketFactory(sslContext
				.getSocketFactory());

		return this;
	}

	/**
	 * Configure HTTPS connection to trust all hosts using a custom
	 * {@link HostnameVerifier} that always returns <code>true</code> for each
	 * host verified
	 * <p>
	 * This method does nothing if the current request is not a HTTPS request
	 * 
	 * @return this request
	 */
	public HttpRequest trustAllHosts() {
		if (!(connection instanceof HttpsURLConnection))
			return this;
		((HttpsURLConnection) connection)
				.setHostnameVerifier(new HostnameVerifier() {

					public boolean verify(String hostname, SSLSession session) {
						return true;
					}
				});
		return this;
	}
}