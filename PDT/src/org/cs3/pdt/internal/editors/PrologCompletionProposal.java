package org.cs3.pdt.internal.editors;

import java.util.HashSet;
import java.util.Set;

import org.cs3.pdt.PDTPlugin;
import org.cs3.pdt.internal.ImageRepository;
import org.cs3.pl.metadata.PrologElementData;
import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;


public class PrologCompletionProposal implements ICompletionProposal {
	
	/** The string to be displayed in the completion proposal popup */
	//private String fDisplayString;
	/** The replacement string */
	//private String fReplacementString;
	/** The replacement offset */
	private int fReplacementOffset;
	/** The replacement length */
	private int fReplacementLength;
	/** The cursor position after this proposal has been applied */
	private int fCursorPosition;
	/** The image to be displayed in the completion proposal popup */
	private Image fImage;
	private PrologElementData data;
	/** The additional info of this proposal */
	//private String fAdditionalProposalInfo;
    private static final Image publicImage = ImageRepository.getImage(ImageRepository.PE_PUBLIC).createImage();
    private static final Image hiddenImage = ImageRepository.getImage(ImageRepository.PE_HIDDEN).createImage();
    private String postfix;
    private Image image;
    private IContextInformation context;
    private String help;

	/**
	 * Creates a new completion proposal based on the provided information.  The replacement string is
	 * considered being the display string too. All remaining fields are set to <code>null</code>.
	 *
	 * @param replacementOffset the offset of the text to be replaced
	 * @param replacementLength the length of the text to be replaced
	 * @param cursorPosition the position of the cursor following the insert relative to replacementOffset
	 */
	public PrologCompletionProposal(PrologElementData data, int replacementOffset, int replacementLength,String prefix) {
		Assert.isTrue(replacementOffset >= 0);
		Assert.isTrue(replacementLength >= 0);
		fReplacementOffset= replacementOffset;
		fReplacementLength= replacementLength;
		this.data=data;
		
		
		
			if(data.getLabel().regionMatches(true,0,prefix,0,prefix.length()) ) {
				
				postfix = "";
                int cursorPos = data.getLabel().length();
				if (data.getArity() > 0) {
					postfix = "()";
					cursorPos++;
				}
				else if (data.getArity() == -1) {
					postfix = ":";
					cursorPos++;
				}
				
				
				image = data.isPublic() ? publicImage : hiddenImage;
                fCursorPosition=cursorPos;
				
		
				
		}
	}

	
	/*
	 * @see ICompletionProposal#apply(IDocument)
	 */
	public void apply(IDocument document) {
		try {
			document.replace(fReplacementOffset, fReplacementLength, data.getLabel() + postfix);
		} catch (BadLocationException x) {
			// ignore
		}
	}
	
	/*
	 * @see ICompletionProposal#getSelection(IDocument)
	 */
	public Point getSelection(IDocument document) {
		return new Point(fReplacementOffset + fCursorPosition, 0);
	}

	/*
	 * @see ICompletionProposal#getContextInformation()
	 */
	public IContextInformation getContextInformation() {	    
        if (context==null && getHelp().length() > 0) {
			int predLen = data.getLabel().length();
			int firstLB = getHelp().indexOf('\n');
			if(firstLB > predLen) {
				String params = getHelp().substring(predLen,firstLB);
				context = new ContextInformation(null, "", params );
			}
		}
		return context;
	}

	/*
	 * @see ICompletionProposal#getImage()
	 */
	public Image getImage() {
		return fImage;
	}

	/*
	 * @see ICompletionProposal#getDisplayString()
	 */
	public String getDisplayString() {
		return data.getSignature();
	}

	/*
	 * @see ICompletionProposal#getAdditionalProposalInfo()
	 */
	public String getAdditionalProposalInfo() {
		return getHelp();
	}


    /**
     * @return
     */
    private String getHelp() {
        if(this.help==null){
            this.help=PDTPlugin.getDefault().getMetaInfoProvider().getHelp(data);
            if(this.help==null){
                this.help="";
            }
        }
        return help;
    }

}
