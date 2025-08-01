'use dom';

import { useEffect } from 'react';
import html2canvas from 'html2canvas';

export default function RecieptDOM({
    onScreenshotReady
}: {
    dom: import('expo/dom').DOMProps,
    onScreenshotReady: (screenshot: string) => Promise<void>
}) {
    // Capture screenshot once the DOM is fully rendered
    useEffect(() => {
        const captureScreenshot = () => {
            console.log("Attempting to capture screenshot...");
            const body = document.getElementById('body');
            if (!body) {
                console.error("Element with ID 'body' not found.");
                return;
            }

            console.log("Body element found, starting html2canvas...");
            html2canvas(body, {
                useCORS: true,
                allowTaint: true,
                scale: 1,
                width: 384 // Standard thermal printer width
            })
                .then(canvas => {
                    const base64Image = canvas.toDataURL("image/png");
                    console.log("Screenshot captured successfully, length:", base64Image.length);
                    // Send screenshot to native component
                    onScreenshotReady(base64Image);
                })
                .catch(error => {
                    console.error("Failed to capture screenshot:", error);
                });
        };

        // Use a longer delay to ensure DOM is fully rendered and styled
        const timer = setTimeout(captureScreenshot, 200);

        return () => clearTimeout(timer);
    }, [onScreenshotReady]); // Re-capture if name changes


    return (
        <html>
            <head>
                <title>Receipt Preview</title>
            </head>
            <div id='body' style={{
                margin: '0',
                backgroundColor: '#f0f8ff',
                fontFamily: 'Arial, sans-serif',
                width: '384px' // Standard thermal printer width
            }}>
                <div style={{
                    margin: '0 auto',
                    backgroundColor: '#ffffff',
                    border: '2px solid #333',
                    borderRadius: '12px',
                    padding: '10px',
                    boxShadow: '0 4px 8px rgba(0,0,0,0.1)'
                }}>
                    <header style={{
                        textAlign: 'center',
                        marginBottom: '20px',
                        borderBottom: '2px solid #eee',
                        paddingBottom: '15px'
                    }}>
                        <h1 style={{
                            color: '#000000',
                            fontSize: '24px',
                            margin: '0 0 5px 0',
                            fontWeight: 'bold'
                        }}>Receipt Preview</h1>
                    </header>

                    <main>
                        <div style={{
                            marginBottom: '20px'
                        }}>
                            <h2 style={{
                                color: '#000000',
                                fontSize: '18px',
                                margin: '0 0 10px 0'
                            }}>Order Details</h2>
                            <div style={{
                                padding: '15px',
                                borderRadius: '8px',
                                border: '1px solid #e9ecef'
                            }}>
                                <div style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    marginBottom: '8px'
                                }}>
                                    <span>Sample Item 1</span>
                                    <span style={{ fontWeight: 'bold' }}>$12.99</span>
                                </div>
                                <div style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    marginBottom: '8px'
                                }}>
                                    <span>Sample Item 2</span>
                                    <span style={{ fontWeight: 'bold' }}>$8.50</span>
                                </div>
                                <hr style={{
                                    border: 'none',
                                    borderTop: '1px solid #dee2e6',
                                    margin: '10px 0'
                                }} />
                                <div style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    fontSize: '18px',
                                    fontWeight: 'bold',
                                    color: '#000000'
                                }}>
                                    <span>Total:</span>
                                    <span>$21.49</span>
                                </div>
                            </div>
                        </div>

                        <footer style={{
                            textAlign: 'center',
                            color: '#000000',
                            fontSize: '12px',
                            marginTop: '20px',
                            paddingTop: '15px',
                            borderTop: '1px solid #eee'
                        }}>
                            <p style={{ margin: '5px 0' }}>Thank you for your business!</p>
                            <p style={{ margin: '5px 0' }}>DOM Component powered by React 19</p>
                            <p style={{ margin: '5px 0' }}>Date: {new Date().toLocaleDateString()}</p>
                        </footer>
                    </main>
                </div>
            </div>
        </html>
    );
}
