# AI Chatbot Frontend - Copilot Instructions

## Project Overview
This is a React-based frontend for an AI Chatbot application with RAG (Retrieval-Augmented Generation) capabilities. The frontend interfaces with a Spring Boot backend at `http://localhost:8080/api/chat` and handles streaming responses for real-time chat interactions.

## Architecture & Key Components

### Backend Integration
- **API Root**: `http://localhost:8080/api/chat`
- **Protocol**: HTTP with streaming response support
- **Key Feature**: RAG - displays retrieved documents alongside chat responses
- Messages sent to backend should trigger streaming responses that need proper handling in the UI

### Frontend Stack
- **Framework**: React 19.2.3 with Create React App
- **Testing**: React Testing Library (with Jest)
- **Styling**: CSS modules (App.css, index.css)
- **Build Tool**: react-scripts 5.0.1

## Expected Features to Implement

### Chat Interface
1. Message input field with send functionality
2. Chat display area showing message history (user and bot messages)
3. Streaming response handling - display partial responses as they arrive from the backend
4. Loading states while awaiting backend responses

### RAG Integration
- Display retrieved documents/sources when the RAG feature is active
- Show document context alongside AI responses
- Ensure proper formatting for document references

### User Experience
- Error handling and user-friendly error messages
- Loading indicators during API calls
- Responsive design for multiple screen sizes
- Proper state management for chat history and API responses

## Development Workflow

### Local Development
```bash
npm start          # Start dev server on http://localhost:3000
npm test           # Run tests in watch mode
npm run build      # Production build
```

### Project Structure
- `src/App.js` - Root component (currently placeholder, needs chat interface implementation)
- `src/App.css` - Main styling
- `src/index.css` - Global styles
- `public/index.html` - HTML entry point
- `src/*.test.js` - Jest test files

## Coding Patterns & Conventions

### React Patterns
- Functional components with hooks (modern React 19)
- Use React StrictMode for development (enabled in index.js)
- Standard CRA linting configuration (eslint extends react-app, react-app/jest)

### API Integration
- Implement streaming fetch requests to handle real-time bot responses
- Handle network errors gracefully with user-facing messages
- Maintain chat history in component state

### Testing
- Use React Testing Library (preferred over Enzyme)
- Follow jest configuration from setupTests.js
- Test user interactions, not implementation details

## Key Files & Their Purpose
- [src/App.js](src/App.js) - Main chat component (needs implementation)
- [src/index.js](src/index.js) - React entry point with StrictMode
- [package.json](package.json) - Dependencies and scripts
- [public/index.html](public/index.html) - HTML shell

## Important Notes
- No existing state management library (Redux/Context) configured - can add if complexity warrants
- No environment configuration visible - may need to add `.env` for API URL configuration
- No API service layer yet - consider creating `src/services/chatService.js` for API calls
- Streaming responses require special fetch handling (not traditional JSON response)
